package no.nav.sifinnsynapi.utils

import org.springframework.util.ResourceUtils
import java.io.File

fun file2ByteArray(file: String): ByteArray = hentFil(file).readBytes()
fun hentFil(file: String): File = ResourceUtils.getFile("${ResourceUtils.CLASSPATH_URL_PREFIX}$file")
