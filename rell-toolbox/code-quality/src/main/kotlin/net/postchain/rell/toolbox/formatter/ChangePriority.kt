package net.postchain.rell.toolbox.formatter

enum class ChangePriority(val priority: Int) {
    HIGH(1),
    DEFAULT(0),
    LOW(-1),
    SUPER_HIGH(2)
}
