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
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static net.minecraft.server.v1_12_R1.PlayerSelector.getPlayer;
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

    @Override
    public void onEnable() {
        File configFile = new File(getDataFolder(), "config.yml");

        if (!configFile.exists()) {
            saveResource("config.yml", false);
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        InputStream defConfigStream = getResource("config.yml");
        if (defConfigStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(defConfigStream);
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
            if (
