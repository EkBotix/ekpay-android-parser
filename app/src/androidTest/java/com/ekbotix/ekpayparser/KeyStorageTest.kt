package com.ekbotix.ekpayparser
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import com.ekbotix.ekpayparser.crypto.AndroidParserKeyStore
import com.ekbotix.ekpayparser.storage.ProtectedStorage
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.io.File

/** Emulator-only runtime validation; never run against an operator's real enrollment. */
@RunWith(AndroidJUnit4::class)
class KeyStorageTest {
    @Test fun protectedKeySurvivesReopenSignsAndDeletionFailsClosed(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val keys=AndroidParserKeyStore(ProtectedStorage(context)){true} // explicit test consent
        val alias=keys.generate()
        try {
            val public=keys.publicKey(alias);val payload="synthetic instrumentation only".toByteArray()
            val reopened=AndroidParserKeyStore(ProtectedStorage(context)){false}
            assertArrayEquals(public,reopened.publicKey(alias))
            val sig=reopened.sign(alias,payload)
            assertTrue(Ed25519Signer().apply{init(false,Ed25519PublicKeyParameters(public,0));update(payload,0,payload.size)}.verifySignature(sig))
            assertTrue(keys.protection(alias).isNotBlank())
        } finally {keys.delete(alias)}
        assertThrows(Exception::class.java){keys.sign(alias,byteArrayOf(1))}
    }
    @Test fun stateCiphertextIsAuthenticatedAndNotPlaintext(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext;val storage=ProtectedStorage(context)
        val label="instrumentation.fixture";val value="TEST_SYNTHETIC_STATE".toByteArray()
        try{storage.write(label,value);assertArrayEquals(value,ProtectedStorage(context).read(label))
            assertFalse(File(context.noBackupFilesDir,"parser-protected/$label").readText().contains("TEST_SYNTHETIC_STATE"))
            val encrypted=storage.encrypt("correct-label",value);assertThrows(Exception::class.java){storage.decrypt("wrong-label",encrypted)}
        }finally{storage.delete(label)}
    }
}
