package ru.nikita22007.wsmdnsproxy.app

import org.apache.commons.text.StringEscapeUtils

internal fun String.xmlText(): String = StringEscapeUtils.escapeXml11(this)
