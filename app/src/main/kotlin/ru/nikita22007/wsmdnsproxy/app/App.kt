package ru.nikita22007.wsmdnsproxy.app

import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) = runBlocking {
    val isWindows = System.getProperty("os.name").contains("Windows", ignoreCase = true)
    val cliArgs = CliParser.parse(args, isWindows)

    if (cliArgs.showHelp) {
        CliParser.printHelp()
        return@runBlocking
    }

    // 1. Загружаем конфиг и применяем параметры CLI
    val baseConfig = ConfigManager.loadConfig()
    val finalConfig = baseConfig.copy(
        isolatedMode = cliArgs.isolatedMode ?: baseConfig.isolatedMode
    )
    
    // 2. Определяем интерфейсы (ручные или авто)
    val interfacesToUse = if (cliArgs.requestedInterfaces.isNotEmpty()) {
        cliArgs.requestedInterfaces
    } else {
        NetworkUtils.getLocalIps().map { InterfaceRequest(it) }
    }

    if (interfacesToUse.isEmpty()) {
        println("Error: No network interfaces found.")
        return@runBlocking
    }

    // 3. Запускаем оркестратор
    ProxyOrchestrator(finalConfig, interfacesToUse).run()
}
