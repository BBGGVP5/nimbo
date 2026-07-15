package com.danila.nimbo

import android.net.Uri
import com.danila.nimbo.network.LinkParser

fun main() {
    val link = "vless://1f1e2aba-f6ee-481e-a2e6-1851e27f2218@213.165.57.142:443?type=tcp&security=reality&pbk=JmE1b7k-gEx_sfwfwd&sni=www.microsoft.com#MyServer"
    val server = LinkParser.parse(link)
    println(server)
}
