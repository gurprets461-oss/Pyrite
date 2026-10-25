package dev.gurpret.pyrite.runner

/**
 * The REPL's interpreter, in its own `:repl` process so it keeps running while
 * scripts run (and die) in `:py`. Same behavior as [PythonService].
 */
class ReplService : PythonService()
