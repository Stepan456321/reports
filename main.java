package org.example;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

public class main extends JavaPlugin {

    private Cache<String, Report> reportCache;
    private FileConfiguration config;
    private String chatMessage;
    private String titleMessage;
    private String actionBarMessage;

    private Connection connection;
    private DSLContext dslContext;
    public int id = 1;
    private PlayerJoinEvent event;

    @Override
    public void onEnable() {
        File configFile = new File(getDataFolder(), "config.yml");

        if (!configFile.exists()) {
            saveResource("config.yml", false);
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        InputStream defConfigStream = getClass().getResourceAsStream("config.yml");
        if (defConfigStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defConfigStream, StandardCharsets.UTF_8));
            config.setDefaults(defConfig);
        }

        try {
            config.save(configFile);
        } catch (IOException ex) {
            getLogger().log(Level.SEVERE, "Не получается сохранить файл " + configFile, ex);
        }
        try {
            Class.forName("com.mysql.jdbc.Driver");
            connection = DriverManager.getConnection("jdbc:mysql:Unknown", "Unknown", "Unknown");
            dslContext = DSL.using(connection, SQLDialect.MYSQL);
        } catch (ClassNotFoundException | SQLException e) {
            e.printStackTrace();
            Bukkit.getPluginManager().disablePlugin(this);

            chatMessage = config.getString("chatMessage");
            titleMessage = config.getString("titleMessage");

            getServer().getPluginManager().registerEvents((Listener) this, this);
            return;
        }

        reportCache = Caffeine.newBuilder()
                .expireAfterWrite(15, TimeUnit.MINUTES)
                .removalListener((key, value, cause) -> {
                    if (cause == com.github.benmanes.caffeine.cache.RemovalCause.EXPIRED) {
                        Report report = (Report) value;
                        assert report != null;
                        dslContext.insertInto(table("reports"))
                                .set(field("ID"), id())
                                .set(field("Player"), report.violator())
                                .set(field("Reason"), report.reason())
                                .set(field("Date"), report.date())
                                .execute();
                    }
                })
                .build();

        getLogger().info("Reports enabled!");
    }

    private Object id() {
        return null;
    }

    @Override
    public void onDisable() {
        try {
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        getLogger().info("Reports disabled!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("report")) {
            if (args.length < 2) {
                sender.sendMessage("Использование: /report <Нарушитель> <Причина>");
                return true;
            }

            String violator = args[0];
            String reason = String.join(" ", args).substring(violator.length() + 1);
            LocalDateTime date = LocalDateTime.now();

            Report report = new Report(violator, reason, date);
            reportCache.put(violator, report);

            sender.sendMessage("Репорт отправлен!");
            id++;

            return true;
        }

        return false;
    }

    private record Report(String violator, String reason, LocalDateTime date) {

    }

    public void onPlayerJoin(PlayerJoinEvent event) {
        this.event = event;
        Cache<String, Integer> reportCache = Caffeine.newBuilder().build();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Integer reportsCount = reportCache.getIfPresent(player.getName());
            if (reportsCount != null  && reportsCount > 2) {
                player.setWalkSpeed(0);
                player.sendMessage(chatMessage);
                player.sendTitle(titleMessage, "");
            }
        }
    }



}
