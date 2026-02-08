# Hibernia Framework

This project is a framework I'm working on for building Minecraft plugins with an emphasis on an Event-Service-Dao architecture. An event in this context is anything which causes an action. In a REST API this would usually be a controller, here it would be a command and or game event.

Check out the AI-Generated documentation which is more-or-less accurate at http://deepwiki.com/paradauxio/hibernia-framework

## Feature List
- Command registration, handling and routing with Paper and Brigaider support.
- Localisation via a templated `properties` file. This uses MiniMessage/Adventure for formatting. 
- Configuration deserialisation and mapping.

Coming soon:
- Event listening and lifecycle
- Bi-directional configuration, have set values be reflected in the configuration file.
- Regularly scheduled task creation (Akin to @Scheduled in Spring)
- 
- PlaceholderAPI support within the localisation module.
- Framework-created and managed Guice modules which you either add to your own injector or use the framework-controlled injector.
- Better documentation...

## Using the framework 

Configure the maven repository:
```gradle
repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    
    // For stable releases
    maven("https://repo.paradaux.io/releases")
    
    // For snapshot builds (optional)
    maven("https://repo.paradaux.io/snapshots")
}
```

```xml
<repositories>
    <repository>
        <id>paradaux-releases</id>
        <url>https://repo.paradaux.io/releases</url>
    </repository>
    
    <!-- Optional: for snapshot builds -->
    <repository>
        <id>paradaux-snapshots</id>
        <url>https://repo.paradaux.io/snapshots</url>
        <snapshots>
            <enabled>true</enabled>
        </snapshots>
    </repository>
</repositories>
```

Configure the dependency:
```gradle
dependencies {
    implementation("io.paradaux:hibernia-framework:1.0.0")
}
```

```xml
<dependencies>
    <dependency>
        <groupId>io.paradaux</groupId>
        <artifactId>hibernia-framework</artifactId>
        <version>1.0.0</version>
    </dependency>
</dependencies>
```

This framework includes some useful additional libraries which you can use in your consuming plugins, the former of which is encouraged.
- Guice 7.0.0
- Reflections

## Features

### 1. Annotation-based declarative Command handling:
```java
@Command("eco")
public class EconomyCommands implements CommandHandler {
    
    @Route("")
    public void root(@Sender Player sender) {
        // Handles: /eco
    }
    
    @Route("balance")
    public void balance(@Sender Player sender) {
        // Handles: /eco balance
    }
    
    @Route("give <player> <amount>")
    public void give(@Sender Player sender, @Arg("player") OfflinePlayer target, @Arg("amount") int amount) {
        // Handles: /eco give <player> <amount>
    }
}
```
See https://deepwiki.com/ParadauxIO/hibernia-framework/4.1-defining-commands for more information. 

It supports Brigaider, and automatically registers the commands with Paper. This means tab-support is provided via the use of "resolvers." Several resolvers are baked-in:
- String
- BigDecimal
- Integer
- OfflinePlayer

It is possible to implement your own by implementing the ParameterResolver interface. The below example auto-completes firms which the player has access to via service method calls.

```java
@Singleton
public final class FirmPlayerResolver implements ParameterResolver<FirmPlayer> {

    private final FirmPlayerService players;

    @Inject
    public FirmPlayerResolver(FirmPlayerService players) {
        this.players = players;
    }

    @Override
    public Class<FirmPlayer> type() {
        return FirmPlayer.class;
    }

    @Override
    public Optional<FirmPlayer> resolve(String token, CommandSender sender) {
        if (token == null || token.isBlank()) return Optional.empty();

        // 1) UUID exact
        UUID asUuid = tryParseUuid(token);
        if (asUuid != null) {
            return players.findByUuid(asUuid);
        }

        // 2) Exact (case-insensitive) name match if present in cache
        Optional<FirmPlayer> byExactName = players.findByName(token);
        if (byExactName.isPresent()) return byExactName;

        // 3) Prefix search – only accept if it’s unambiguous
        List<FirmPlayer> matches = players.searchByPrefix(token, 5);
        if (matches.size() == 1) {
            return Optional.of(matches.get(0));
        }

        // ambiguous or no match
        return Optional.empty();
    }

    @Override
    public List<String> suggestions(String prefix, CommandSender sender) {
        String p = (prefix == null) ? "" : prefix.trim();
        // Return last-known names for tab completion
        return players.searchByPrefix(p, 20).stream()
                .map(FirmPlayer::getCurrentName)
                .distinct()
                .toList();
    }

    private static UUID tryParseUuid(String s) {
        try {
            String norm = s.trim();
            // Support compact 32-char UUID too, because players paste weird stuff
            if (norm.length() == 32) {
                norm = norm.substring(0, 8) + "-" + norm.substring(8, 12) + "-" +
                        norm.substring(12, 16) + "-" + norm.substring(16, 20) + "-" +
                        norm.substring(20);
            }
            return UUID.fromString(norm.toLowerCase(Locale.ROOT));
        } catch (Exception ignored) {
            return null;
        }
    }
}
``` 

### 2. Exceptions

Within the framework there are semantic exceptions you can throw from within service methods, with an eye to building a rich-error experience:
- BadCommandException: Used when commands are malformatted semantically, but were executed syntactically correct. Analogous to a HTTP 400
- ConflictException: When a command tries to do something which causes a conflict (e.g., for use with an RDMS with a primary key / unique constraint violation.) Analogous to a HTTP 409
- ExceedsLimitException: When a command sender attempts to exceed a limit imposed by a command (cooldowns, value constraints etc) 
- InternalException: When an unexpected error occurs within the framework, or within a consuming plugin.
- NoPermissionException: Thrown internally to the framework when permission checks fail, as these are thrown within the framework, it is not possible to catch within an Event/Command
- NotFoundException: When a resource is not found. Analogous to a HTTP 404
  
These extend RuntimeException, in the future they will extend generic `CommandException` and or `EventException` once the exception functionality is more built out. 

The intended pattern is that you would throw these exceptions within your service layer, and catch them in your command handler with a message associated, or allow the framework-defaults.

### 3. Configuration

This framework provides yaml deserialisation to objects based on annotations, analogous to @Value in Spring Framework. 

```java
@ConfigurationComponent
@Getter
public class DatabaseConfiguration {

    @ConfigurationValue(path = "database.host", defaultValue = "localhost")
    private String host;

    @ConfigurationValue(path = "database.port", defaultValue = "3306")
    private String port;

    @ConfigurationValue(path = "database.database", defaultValue = "treasury")
    private String database;

    @ConfigurationValue(path = "database.username", defaultValue = "root")
    private String username;

    @ConfigurationValue(path = "database.password", defaultValue = "password")
    private String password;

    @ConfigurationValue(path = "database.table-prefix", defaultValue = "treasury_")
    private String tablePrefix;
}
```

The path is used to represent the path within config.yml where the value is found. If the value is undefined it takes the default value. Empty string is a valid value which won't be replaced with the default value.
The framework contains the logic to scan a package for ConfigurationComponent-annotated classes and create a Singleton instance for dependency injection purposes. Two-way syncronisaiton between the Singleton and the configuration file is not yet supported.

### Localisation and Internationalisation

The framework includes a simple templated localisation system, which allows for the use of a `messages.properties` and a `Message` class which has helper methods for sending messages using keys from this file, along with key-value placeholders and their replacements.

Example:
```properties
# Static Placeholders
# Anything after placeholder. is treated as a placeholder, which can be used in any of the below
# localisation configurations, you can define as many as you want here, and each are supported automatically.
placeholder.prefix=<bold><blue>BUSINESS</blue></bold> <gray>»</gray>

# Below are placeholders to use to keep a consistent colour palette. sec for secondary, then the beginning and ending tags.
placeholder.secbegin=<#6f6fff>
placeholder.secend=</#6f6fff>
placeholder.secgt=<#6f6fff><</#6f6fff>
placeholder.seclt=<#6f6fff>></#6f6fff>

business.general.no-permission={prefix} You do not have permission to run this command.
business.firm.disband.success={prefix} {firm} has been disbanded, and firms have been returned to the proprietor's account.
business.firm.disband.broadcast={prefix} {secbegin}{firm}{secend} has been disbanded by {secbegin}{sender}{secend}.
```

 This includes the ability to create an arbitrary number of static placeholders within the `placeholder` namespace, which will automatically be replaced, such as `{prefix}` below defined as `placeholder.prefix`
```java
    Message message;
    
    @Route("disband <firm>")
    @Permission("business.disband")
    @Description("Disband a firm you own")
    public void disband(@Sender Player sender, @Arg("firm") String firm) {
        Firm f = firms.getFirmByNameOrId(firm);
        if (!firms.isProprietor(firm, sender.getUniqueId())) {
            message.send(sender, "business.general.no-permission");
            return;
        }
    
        firms.disbandFirm(firm, sender.getUniqueId());
        message.send(sender, "business.firm.disband.success", "firm", firm);
        message.broadcast("business.firm.disband.broadcast", "firm", firm, "sender", sender.getName());
    }
```

A sender has to be either a CommandSender (e.g., Player) or a custom class which implements the `HiberniaPlayer` interface. This is then followed by the key-value pairs as varargs, where the first value is the placeholder you wish to replace, and the second is the value to put to that placeholder. 
This is done in the above example to fill in the firm name in this business/company plugin.

## Guice Glue

It's expected to use this framework in conjunction with Guice, if you are not familiar with Guice use other resources at first to get yourself acquainted with the library or the general principles of dependency injection. 

It's intended to abstract this module creation away into the framework itself, with the ability to add additional module by a framework-controlled injector, although this is not yet implemted. Below are examples of the guice configuration for another of my plugins.

### Main module

Registers the JavaPlugin instance (Business in this example) as well as services. 
```java
public class BusinessModule extends AbstractModule {

    private final Business business;
    private final ConfigurationLoader configurationLoader;

    public BusinessModule(Business business, ConfigurationLoader configurationLoader) {
        this.business = business;
        this.configurationLoader = configurationLoader;
    }

    @Override
    protected void configure() {
        bind(Business.class).toInstance(business);
        bind(ConfigurationLoader.class).toInstance(configurationLoader);

        // Automatically bind all configuration components
        for (Map.Entry<Class<?>, Object> entry : configurationLoader.getComponents().entrySet()) {
            @SuppressWarnings("unchecked")
            Class<Object> key = (Class<Object>) entry.getKey();
            Object value = entry.getValue();
            bind(key).toInstance(value);
        }

        // Framework Beans
        bind(Message.class).asEagerSingleton();

        // Bind services
        bind(FirmAreaShopService.class).to(FirmAreaShopServiceImpl.class).in(Singleton.class);
        bind(FirmRoleService.class).to(FirmRoleServiceImpl.class).in(Singleton.class);
        bind(FirmSalesService.class).to(FirmSalesServiceImpl.class).in(Singleton.class);
        bind(FirmService.class).to(FirmServiceImpl.class).in(Singleton.class);
        bind(FirmStaffService.class).to(FirmStaffServiceImpl.class).in(Singleton.class);
        bind(FirmTransactionService.class).to(FirmTransactionServiceImpl.class).in(Singleton.class);
        bind(FirmRequestService.class).to(FirmRequestServiceImpl.class).in(Singleton.class);
        bind(FirmPlayerService.class).to(FirmPlayerServiceImpl.class).in(Singleton.class);

        // Bind Jobs
        bind(ExpireRequestsJob.class).in(Singleton.class);
    }
}
``` 

### Commander module
This is the module which registers argument resolvers (see https://deepwiki.com/ParadauxIO/hibernia-framework/4.3-parameter-resolvers) as well as the commands themselves. I also register the Internationalisation portion of the framework here.




```java
public final class CommanderModule extends AbstractModule {

    private final JavaPlugin plugin;

    public CommanderModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    protected void configure() {
        // Bind both JavaPlugin and Plugin to the running plugin instance
        bind(JavaPlugin.class).toInstance(plugin);
        bind(Plugin.class).toInstance(plugin);

        Multibinder<CommandHandler> handlerBinder =
                Multibinder.newSetBinder(binder(), CommandHandler.class);

        Multibinder<ParameterResolver<?>> prm =
                Multibinder.newSetBinder(binder(), new TypeLiteral<>() {});
        prm.addBinding().to(FirmPlayerResolver.class);

        handlerBinder.addBinding().to(FirmCommands.class);
        handlerBinder.addBinding().to(HelpCommands.class);
        handlerBinder.addBinding().to(MiscCommands.class);
        handlerBinder.addBinding().to(RequestCommands.class);
        handlerBinder.addBinding().to(RoleCommands.class);
        handlerBinder.addBinding().to(StaffCommands.class);
        handlerBinder.addBinding().to(ReloadCommand.class);
        handlerBinder.addBinding().to(TestCommand.class);
    }
}
```

## Guice enablement / Framework initialisation

Creating the injector/initialising the framework in on enable:
```java
   @Override
    public void onEnable() {
        getLogger().info("Loading configuration...");

        // 1) Load typed config components
        ConfigurationLoader configLoader = new ConfigurationLoader(this);
        configLoader.scanPackage("net.democracycraft.business.model.config"); // Wherever your @ConfigurationComponent beans are 

        // 2) Create the injector, wiring:
        //    - BusinessModule (binds plugin + all config components)
        //    - DatabaseModule (needs the typed DatabaseConfiguration)
        //    - CommanderModule (commands)
        getLogger().info("Setting up dependency injection...");
        this.injector = Guice.createInjector(
                new BusinessModule(this, configLoader),
                new CommanderModule(this)
        );

        // 3) Register commands (DI-managed)
        injector.getInstance(CommandManager.class).registerAll();
    }
``` 

At this point, if you managed to get all that working with this shoddy documentation, hats off to you. It should be good enough for an experienced developer who isn't afraid of looking into library internals.
A proper wiki will be created when it's intended that third-party contributions to the framework are possible, and that there are other plugins making use.

