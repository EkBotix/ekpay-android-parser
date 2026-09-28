package com.ekbotix.ekpayparser
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
class ManifestPolicyTest {
    @Test fun onlyReceiveSmsAndNoRawSmsWorkPayload(){
        val file=File("src/main/AndroidManifest.xml");assertTrue(file.exists())
        val text=file.readText();val xml=DocumentBuilderFactory.newInstance().apply{isNamespaceAware=true}.newDocumentBuilder().parse(file)
        val permissions=xml.getElementsByTagName("uses-permission")
        val names=(0 until permissions.length).map{permissions.item(it).attributes.getNamedItemNS("http://schemas.android.com/apk/res/android","name").nodeValue}
        assertEquals(setOf("android.permission.INTERNET","android.permission.ACCESS_NETWORK_STATE", "android.permission.RECEIVE_SMS"),names.toSet())

        assertTrue(xml.getElementsByTagName("receiver").length >= 1)
        assertTrue(text.contains("android:usesCleartextTraffic=\"false\""));assertTrue(text.contains("android:allowBackup=\"false\""))
        for(permission in listOf("READ_SMS","SEND_SMS","READ_CONTACTS","READ_PHONE_STATE"))assertFalse(text.contains(permission))
        val receiver=File("src/main/java/com/ekbotix/ekpayparser/sms/SmsReceiver.kt").readText()
        val normalizedWorkData=receiver.substringAfter("workDataOf(").substringBefore(")")
        assertFalse(normalizedWorkData.contains("\"body\""));assertFalse(normalizedWorkData.contains("\"sender\""))
        val releaseTelemetry=File("src/release/java/com/ekbotix/ekpayparser/sms/ReceiverTelemetry.kt").readText()
        assertFalse(releaseTelemetry.contains("android.util.Log"));assertFalse(releaseTelemetry.contains("MessageDigest"))
    }
}
