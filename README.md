# PluginUpdater

Jenkins でビルドされてるプラグインをサーバー終了時アップデートできる物。

BungeeCord、Bukkit に対応

# Setup

## プラグインバージョン

あなたのプラグインバージョンは以下のフォーマットにする必要があります。

```yaml
version: git:${project.name}:${project.version}:${SHA}:${build.number}
```

## Jenkins ビルド引数

`シェルの実行` などで、以下のシェルコマンドにビルドコマンドを設定してください。

```
mvn clean package -Dbuild.number=${BUILD_NUMBER} -DSHA=$(git rev-parse --short HEAD)
```

## サンプルコード

`onEnable` で、`new PluginUpdater().initBukkit(this);` または `initBungee(this);` を実行してください。

```java
@Override
public void onEnable() {
	// Bukkit
	new PluginUpdater().initBukkit(this);

	// Bungee
	new PluginUpdater().initBungee(this);
}
```

# Maven Repository

- Repository
```XML
  <repositories>
    <repository>
      <id>net.simplyrin</id>
      <url>https://api.simplyrin.net/maven/</url>
    </repository>
  </repositories>
```

- Dependency
```XML
  <dependencies>
    <dependency>
      <groupId>net.simplyrin.pluginupdater</groupId>
      <artifactId>PluginUpdater</artifactId>
      <version>1.0</version>
    </dependency>
  </dependencies>
```
