package ru.nikita22007.wsmdnsproxy.app

fun main(args: Array<String>) {
    val cliArgs = try {
        CliParser.parse(args)
    } catch (e: CliUsageException) {
        System.err.println("Error: ${e.message}")
        System.err.println("Use --help or /help for usage.")
        return
    }

    if (cliArgs.showHelp) {
        CliParser.printHelp()
        return
    }

    // 1. Загружаем конфиг и применяем параметры CLI
    val baseConfig = ConfigManager.loadConfig()
    val finalConfig = baseConfig.copy(
        isolatedMode = cliArgs.isolatedMode ?: baseConfig.isolatedMode
    )
    
    // 2. Определяем интерфейсы (ручные или авто)
    val interfacesToUse = if (cliArgs.publishInterfaces.isNotEmpty()) {
        cliArgs.publishInterfaces
    } else {
        NetworkUtils.getLocalIps().map { InterfaceRequest(it) }
    }

    if (interfacesToUse.isEmpty()) {
        println("Error: No network interfaces found.")
        return
    }

    // 3. Запускаем оркестратор
    ProxyOrchestrator(finalConfig, cliArgs.listenInterfaces, interfacesToUse).run()
}
