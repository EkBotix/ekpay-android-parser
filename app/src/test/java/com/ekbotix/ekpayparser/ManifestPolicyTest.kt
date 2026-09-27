package com.ekbotix.ekpayparser
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
class ManifestPolicyTest {
    @Test fun noSmsPermissionsReceiversCleartextBackupOrSensitiveIdentifiers(){
        val file=File("src/main/AndroidManifest.xml");assertTrue(file.exists())
        val text=file.readText();val xml=DocumentBuilderFactory.newInstance().apply{isNamespaceAware=true}.newDocumentBuilder().parse(file)
        val permissions=xml.getElementsByTagName("uses-permission")
        val names=(0 until permissions.length).map{permissions.item(it).attributes.getNamedItemNS("http://schemas.android.com/apk/res/android","name").nodeValue}
        assertEquals(setOf("android.permission.INTERNET","android.permission.ACCESS_NETWORK_STATE"),names.toSet())
        assertEquals(0,xml.getElementsByTagName("receiver").length)
        assertTrue(text.contains("android:usesCleartextTraffic=\"false\""));assertTrue(text.contains("android:allowBackup=\"false\""))
        for(permission in listOf("READ_SMS","RECEIVE_SMS","SEND_SMS","READ_CONTACTS","READ_PHONE_STATE"))assertFalse(text.contains(permission))
    }
}
