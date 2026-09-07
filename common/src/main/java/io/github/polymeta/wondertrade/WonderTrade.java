package io.github.polymeta.wondertrade;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonPropertyExtractor;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import io.github.polymeta.wondertrade.commands.RegeneratePool;
import io.github.polymeta.wondertrade.commands.Reload;
import io.github.polymeta.wondertrade.commands.Trade;
import io.github.polymeta.wondertrade.configuration.BaseConfig;
import io.github.polymeta.wondertrade.configuration.Pool;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;


public class WonderTrade {
    public static final String MOD_ID = "wondertrade";
    public static final MiniMessage miniMessage = MiniMessage.miniMessage();

    public static BaseConfig config;
    public static Pool pool;
    /** Guards every read/write of {@link Pool#pokemon}, which the worker thread and the
     *  server thread both touch. Upstream shared a bare ArrayList across both. */
    public static final Object poolLock = new Object();
    public static AtomicBoolean regenerating = new AtomicBoolean(false);
    public static ScheduledThreadPoolExecutor scheduler;
    public static ForkJoinPool worker;

    private static final Random rng = new Random();
    private static final Logger logger = LogManager.getLogger();

    public static void init() {
        logger.info("WonderTrade by Polymeta starting up!");
        scheduler = new ScheduledThreadPoolExecutor(1, r -> {
            Thread thread = Executors.defaultThreadFactory().newThread(r);
            thread.setName("WonderTrade Thread");
            return thread;
        });
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        worker = new ForkJoinPool(16, new WorkerThreadFactory(), new ExceptionHandler(), false);
        //load config and pool and message configuration
        loadConfig();
        loadPool();

        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) -> {
            RegeneratePool.register(dispatcher);
            Trade.register(dispatcher);
            Reload.register(dispatcher);
        });
        LifecycleEvent.SERVER_STARTED.register((instance) -> {
            if(WonderTrade.pool.pokemon.isEmpty()) {
                logger.info("Regenerating pool as it is empty");
                WonderTrade.regeneratePool(WonderTrade.config.poolSize);
            }
        });
        LifecycleEvent.SERVER_STOPPING.register(instance -> {
            scheduler.shutdownNow();
            worker.shutdownNow();
        });
    }

    public static void regeneratePool(int size)
    {
        if(size <= 0) {
            logger.error("Refusing to regenerate the WonderTrade pool with size {} - poolSize must be at least 1. " +
                         "Fix poolSize in config/wondertrade/main.json and run /regenerate.", size);
            return;
        }
        // Claim the flag before dispatching: upstream checked it here but set it inside the
        // worker, so two calls in the same tick could both start a regeneration.
        if(!regenerating.compareAndSet(false, true))
        {
            return;
        }
        worker.execute(() -> {
            try {
                var randomProp = PokemonProperties.Companion.parse("species=random");
                var blacklist = config.blacklist.stream().map(PokemonProperties.Companion::parse).toList();

                var minLevel = Math.max(1, config.poolMinLevel);
                var maxLevel = Math.min(Cobblemon.config.getMaxPokemonLevel(), config.poolMaxLevel);
                if(maxLevel < minLevel) {
                    maxLevel = minLevel;
                }

                // Generate into a scratch list. Upstream cleared the live pool first, which left
                // every trade crashing for the whole duration of the regeneration.
                var generated = new ArrayList<String>(size);
                for (int i = 0; i < size; i++) {
                    // +1: nextInt(origin, bound) is exclusive at the top, so upstream could never
                    // roll poolMaxLevel. It also threw when origin == bound (poolMinLevel == poolMaxLevel).
                    randomProp.setLevel(minLevel == maxLevel ? minLevel : rng.nextInt(minLevel, maxLevel + 1));
                    Pokemon pokemon = null;
                    for (int retry = 0; retry < 50; retry++) {
                        final Pokemon candidate = randomProp.create();
                        if(blacklist.stream().noneMatch(prop -> prop.matches(candidate)))
                        {
                            pokemon = candidate;
                            break;
                        }
                    }
                    if(pokemon == null)
                    {
                        logger.error("Gave up finding a non-blacklisted Pokemon after 50 attempts. Keeping the " +
                                     "existing pool; review the blacklist in config/wondertrade/main.json.");
                        return;
                    }
                    generated.add(pokemon.createPokemonProperties(PokemonPropertyExtractor.ALL).asString(" "));
                }

                synchronized (poolLock) {
                    pool.pokemon.clear();
                    pool.pokemon.addAll(generated);
                }
                savePool();
                logger.info("Regenerated the WonderTrade pool with {} Pokemon.", generated.size());
            } catch (Exception e) {
                logger.error("Failed to regenerate the WonderTrade pool", e);
            } finally {
                // Upstream never reset this on an early return or an exception, which wedged
                // regeneration for the rest of the server's uptime.
                regenerating.set(false);
            }
        });
    }

    /**
     * Atomically takes a random Pokemon out of the pool and puts {@code deposit} in its place.
     *
     * @return the properties string drawn from the pool, or {@code null} if the pool is empty
     *         (in which case nothing was deposited).
     */
    public static String drawAndDeposit(String deposit) {
        synchronized (poolLock) {
            if(pool.pokemon.isEmpty()) {
                return null;
            }
            var drawn = pool.pokemon.remove(rng.nextInt(pool.pokemon.size()));
            pool.pokemon.add(deposit);
            return drawn;
        }
    }

    /** Undoes the deposit half of {@link #drawAndDeposit} when the drawn entry turned out to be unusable. */
    public static void rollbackDeposit(String deposit) {
        synchronized (poolLock) {
            pool.pokemon.remove(deposit);
        }
    }

    /** Undoes {@link #drawAndDeposit} entirely, putting the drawn entry back and taking the deposit out. */
    public static void restoreDrawn(String drawn, String deposit) {
        synchronized (poolLock) {
            pool.pokemon.remove(deposit);
            pool.pokemon.add(drawn);
        }
    }

    /** A snapshot of the pool, safe to iterate off the lock (used by the pool GUI). */
    public static List<String> poolSnapshot() {
        synchronized (poolLock) {
            return new ArrayList<>(pool.pokemon);
        }
    }

    public static void loadConfig() {
        var configFile = new File("config/wondertrade/main.json");
        configFile.getParentFile().mkdirs();

        // Check config existence and load if it exists, otherwise create default.
        if (configFile.exists()) {
            try {
                var fileReader = new FileReader(configFile);
                config = BaseConfig.GSON.fromJson(fileReader, BaseConfig.class);
                fileReader.close();
            } catch (Exception e) {
                logger.error("Failed to load the config! Using default config as fallback");
                e.printStackTrace();
                config = new BaseConfig();
            }

        } else {
            config = new BaseConfig();
        }
        if(config.poolSize <= 0) {
            var fallback = new BaseConfig().poolSize;
            logger.warn("poolSize must be at least 1 (config said {}); falling back to {}. A poolSize of 0 leaves the " +
                        "pool permanently empty and makes every trade fail.", config.poolSize, fallback);
            config.poolSize = fallback;
        }
        if(config.poolMinLevel > config.poolMaxLevel) {
            logger.warn("Pool min level can not be bigger than max level, adjusting range to 1-CobbleMaxLevel...");
            config.poolMinLevel = 1;
            config.poolMaxLevel = Cobblemon.config.getMaxPokemonLevel();
        }

        saveConfig();
    }

    private static void loadPool() {
        var configFile = new File("config/wondertrade/pool.json");
        configFile.getParentFile().mkdirs();

        // Check config existence and load if it exists, otherwise create default.
        if (configFile.exists()) {
            try {
                var fileReader = new FileReader(configFile);
                pool = BaseConfig.GSON.fromJson(fileReader, Pool.class);
                fileReader.close();
                if(pool == null || pool.pokemon == null) {
                    logger.warn("config/wondertrade/pool.json was empty or malformed; starting from an empty pool.");
                    pool = new Pool();
                }
            } catch (Exception e) {
                logger.error("Failed to load pre-existing wondertrade pool! Removing broken file...");
                e.printStackTrace();
                pool = new Pool();
            }

        } else {
            pool = new Pool();
        }

        savePool();
    }

    private static void saveConfig() {
        try {
            var configFile = new File("config/wondertrade/main.json");
            var fileWriter = new FileWriter(configFile);
            BaseConfig.GSON.toJson(config, fileWriter);
            fileWriter.flush();
            fileWriter.close();
        } catch (Exception e) {
            logger.error("Failed to save the config!");
            e.printStackTrace();
        }
    }

    public static void savePool() {
        try {
            var configFile = new File("config/wondertrade/pool.json");
            var fileWriter = new FileWriter(configFile);
            synchronized (poolLock) {
                BaseConfig.GSON.toJson(pool, fileWriter);
            }
            fileWriter.flush();
            fileWriter.close();
        } catch (Exception e) {
            logger.error("Failed to save the wondertrade pool!");
            e.printStackTrace();
        }
    }


    private static final class WorkerThreadFactory implements ForkJoinPool.ForkJoinWorkerThreadFactory {
        private static final AtomicInteger COUNT = new AtomicInteger(0);

        @Override
        public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
            ForkJoinWorkerThread thread = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
            thread.setDaemon(true);
            thread.setName("WonderTrade Worker - " + COUNT.getAndIncrement());
            thread.setContextClassLoader(WonderTrade.class.getClassLoader());
            return thread;
        }
    }

    private static final class ExceptionHandler implements Thread.UncaughtExceptionHandler {
        @Override
        public void uncaughtException(Thread t, Throwable e) {
            logger.error("Thread " + t.getName() + " threw an uncaught exception");
            e.printStackTrace();
        }
    }
}
