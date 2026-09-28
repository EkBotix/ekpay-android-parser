package com.ekbotix.ekpayparser
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
class ManifestPolicyTest {
    @Test fun internalPermissionsAndComponentsRemainBounded(){
        val file=File("src/main/AndroidManifest.xml");assertTrue(file.exists())
        val text=file.readText();val xml=DocumentBuilderFactory.newInstance().apply{isNamespaceAware=true}.newDocumentBuilder().parse(file)
        val permissions=xml.getElementsByTagName("uses-permission")
        val names=(0 until permissions.length).map{permissions.item(it).attributes.getNamedItemNS("http://schemas.android.com/apk/res/android","name").nodeValue}
        assertEquals(setOf("android.permission.INTERNET","android.permission.ACCESS_NETWORK_STATE", "android.permission.RECEIVE_SMS",
            "android.permission.READ_SMS","android.permission.RECEIVE_BOOT_COMPLETED"),names.toSet())

        assertTrue(xml.getElementsByTagName("receiver").length >= 1)
        assertTrue(text.contains("android:usesCleartextTraffic=\"false\""));assertTrue(text.contains("android:allowBackup=\"false\""))
        for(permission in listOf("SEND_SMS","READ_CONTACTS","READ_CALL_LOG","WRITE_CALL_LOG","READ_PHONE_STATE","ACCESS_FINE_LOCATION"))assertFalse(text.contains(permission))
        assertTrue(text.contains("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"))
        val receiver=File("src/main/java/com/ekbotix/ekpayparser/sms/SmsReceiver.kt").readText()
        assertFalse(receiver.contains("workDataOf"));assertFalse(receiver.contains("Log."))
        val releaseTelemetry=File("src/release/java/com/ekbotix/ekpayparser/sms/ReceiverTelemetry.kt").readText()
        assertFalse(releaseTelemetry.contains("android.util.Log"));assertFalse(releaseTelemetry.contains("MessageDigest"))
    }
}
