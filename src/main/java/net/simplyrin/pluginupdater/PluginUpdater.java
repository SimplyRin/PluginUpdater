package net.simplyrin.pluginupdater;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

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
	
	private final String name;
	private final String version;
	private final int currentBuildNumber;
	
	private final File dataFolder;
	private File pluginJar;
	
	private boolean enabled;
	
	private String url;
	private String fileTo;
	
	private boolean basicAuthEnabled;
	private String username;
	private String password;
	
	public PluginUpdater(JavaPlugin plugin) {
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
		
		this.initConfig();
		
		this.addShutdownHook();
	}
	
	public PluginUpdater(Plugin plugin) {
		this.name = plugin.getDescription().getName();
		this.version = plugin.getDescription().getVersion();
		this.currentBuildNumber = Integer.valueOf(this.version.split("[:]")[4]);
		
		this.dataFolder = plugin.getDataFolder();
		this.pluginJar = plugin.getFile();
		
		this.initConfig();
		
		this.addShutdownHook();
	}
	
	public void initConfig() {
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
	}
	
	private void addShutdownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				if (!enabled) {
					return;
				}

				// checking latest artifact
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
					
					// Jar を移動
					pluginJar.renameTo(new File(fileTo, pluginJar.getName() + "." + UUID.randomUUID().toString().split("-")[0] + "_DISABLED"));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

}
