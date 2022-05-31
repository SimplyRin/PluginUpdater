package net.simplyrin.pluginupdater;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.logging.Logger;

import javax.net.ssl.HttpsURLConnection;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.bukkit.plugin.java.JavaPlugin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import lombok.var;
import net.md_5.bungee.api.plugin.Plugin;
import net.simplyrin.config.Config;
import net.simplyrin.config.Configuration;

/**
 * Created by SimplyRin on 2022/05/23.
 *
 * Copyright (c) 2022 SimplyRin
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
public class PluginUpdater {
	
	private Logger logger;
	
	private String name;
	private String version;
	private int currentBuildNumber;
	
	private File dataFolder;
	private File pluginJar;
	
	private boolean enabled;
	
	private String url;
	private String fileTo;
	
	private boolean basicAuthEnabled;
	private String username;
	private String password;
	
	public PluginUpdater initBukkit(JavaPlugin plugin) {
		return this.initBukkit(plugin, null);
	}
	
	public PluginUpdater initBukkit(JavaPlugin plugin, ConfigData configData) {
		this.logger = plugin.getLogger();
		this.info("Initializing...");
		
		this.name = plugin.getDescription().getName();
		this.version = plugin.getDescription().getVersion();
		this.currentBuildNumber = Integer.valueOf(this.version.split("[:]")[4]);
		
		this.dataFolder = plugin.getDataFolder();
		try {
			Method getFile = JavaPlugin.class.getDeclaredMethod("getFile");
			getFile.setAccessible(true);
			this.pluginJar = (File) getFile.invoke(plugin);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		this.info("Plugin jar: " + this.pluginJar.getPath());
		
		this.initConfig(configData);
		this.addShutdownHook();

		return this;
	}

	public PluginUpdater initBungee(Plugin plugin) {
		return this.initBungee(plugin, null);
	}
	
	public PluginUpdater initBungee(Plugin plugin, ConfigData configData) {
		this.logger = plugin.getLogger();
		this.info("Initializing...");
		
		this.name = plugin.getDescription().getName();
		this.version = plugin.getDescription().getVersion();
		this.currentBuildNumber = Integer.valueOf(this.version.split("[:]")[4]);
		
		this.dataFolder = plugin.getDataFolder();
		this.pluginJar = plugin.getFile();
		
		this.info("Plugin jar: " + this.pluginJar.getPath());
		
		this.initConfig(configData);
		this.addShutdownHook();

		return this;
	}
	
	private PluginUpdater initConfig(ConfigData configData) {
		this.info("Loading updater.yml...");
		
		if (configData != null) {
			this.enabled = configData.isEnabled();
			
			this.url = configData.getJenkinsJobUrl();
			this.fileTo = configData.getMoveOldFileTo();
			
			this.basicAuthEnabled = configData.isBasicAuthEnabled();
			this.username = configData.getUsername();
			this.password = configData.getPassword();
			
			return this;
		}
		
		File file = new File(this.dataFolder, "updater.yml");
		if (!file.exists()) {
			try {
				file.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			var config = new Configuration();
			config.set("Enabled", false);
			config.set("Jenkins-Job-Url", "https://JENKINS_URL/job/" + this.name + "/");
			config.set("Move-Old-File-To", "./plugins/" + this.name + "/.old-files");
			config.set("BasicAuth.Enabled", false);
			config.set("BasicAuth.Username", "name");
			config.set("BasicAuth.Password", "pass");
			Config.saveConfig(config, file);
		}

		final Configuration config = Config.getConfig(file);
		
		this.enabled = config.getBoolean("Enabled");
		
		this.url = config.getString("Jenkins-Job-Url");
		this.fileTo = config.getString("Move-Old-File-To");
		
		this.basicAuthEnabled = config.getBoolean("BasicAuth.Enabled");
		this.username = config.getString("BasicAuth.Username");
		this.password = config.getString("BasicAuth.Password");
		
		return this;
	}
	
	// アップデート確認
	public UpdateInfo checkUpdate() {
		try {
			var connection = (HttpsURLConnection) new URL(this.url + "lastStableBuild/api/json").openConnection();
			connection.addRequestProperty("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/101.0.4951.67 Safari/537.36");
			if (this.basicAuthEnabled) {
				connection.setRequestProperty("Authorization", "Basic " + Base64.getEncoder().encodeToString((this.username + ":" + this.password).getBytes()));
			}
			
			var result = IOUtils.toString(connection.getInputStream(), StandardCharsets.UTF_8);
			
			JsonObject json = new JsonParser().parse(result).getAsJsonObject();
			
			int buildNumber = json.get("number").getAsInt();
			if (buildNumber == Integer.valueOf(this.currentBuildNumber)) {
				return new UpdateInfo(false, this.currentBuildNumber, buildNumber, null, null);
			}
			
			JsonArray artifacts = json.get("artifacts").getAsJsonArray();
			if (artifacts.size() == 0) {
				return new UpdateInfo(true, this.currentBuildNumber, buildNumber, null, null);
			}
			
			JsonObject child = artifacts.get(0).getAsJsonObject();
			String relativePath = child.get("relativePath").getAsString();
			
			String projectUrl = url + buildNumber + "/";
			String artifactUrl = url + "lastStableBuild/artifact/" + relativePath;
			
			return new UpdateInfo(true, this.currentBuildNumber, buildNumber, projectUrl, artifactUrl);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return null;
	}
	
	// シャットダウン時、プラグインのアップデートを実行させる
	private PluginUpdater addShutdownHook() {
		if (!enabled) {
			return this;
		}
		
		this.info("Adding Shutdown Hook...");

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			// 最終安定ビルドの確認
			try {
				var connection = (HttpsURLConnection) new URL(url + "lastStableBuild/api/json").openConnection();
				connection.addRequestProperty("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/101.0.4951.67 Safari/537.36");
				if (basicAuthEnabled) {
					connection.setRequestProperty("Authorization", "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes()));
				}
				
				var result = IOUtils.toString(connection.getInputStream(), StandardCharsets.UTF_8);
				
				JsonObject json = new JsonParser().parse(result).getAsJsonObject();
				
				int buildNumber = json.get("number").getAsInt();
				if (buildNumber == Integer.valueOf(currentBuildNumber)) {
					return;
				}
				
				// 更新あり
				JsonArray artifacts = json.get("artifacts").getAsJsonArray();
				if (artifacts.size() == 0) {
					return;
				}
				
				JsonObject child = artifacts.get(0).getAsJsonObject();
				
				String fileName = child.get("fileName").getAsString();
				String relativePath = child.get("relativePath").getAsString();

				String base = FilenameUtils.getBaseName(fileName);
				String ext = FilenameUtils.getExtension(fileName);
				
				File plugins = new File("plugins");
				File target = new File(plugins, base + "-v" + buildNumber + "." + ext);
				
				// 最新ファイルをダウンロード
				connection = (HttpsURLConnection) new URL(url + "lastStableBuild/artifact/" + relativePath).openConnection();
				connection.addRequestProperty("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/101.0.4951.67 Safari/537.36");
				if (basicAuthEnabled) {
					connection.setRequestProperty("Authorization", "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes()));
				}
				
				// 保存
				FileUtils.copyInputStreamToFile(connection.getInputStream(), target);
				
				File mt = new File(fileTo);
				mt.mkdirs();
				
				// Jar を移動
				pluginJar.renameTo(new File(mt, pluginJar.getName() + "." + UUID.randomUUID().toString().split("-")[0] + "_DISABLED"));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}));
		
		return this;
	}
	
	public void info(String message) {
		if (this.logger != null) {
			this.logger.info("[PluginUpdater] " + message);
		}
	}

}
