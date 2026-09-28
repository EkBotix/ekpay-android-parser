package com.ekbotix.ekpayparser.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.ekbotix.ekpayparser.sms.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import android.app.Application
import com.ekbotix.ekpayparser.*
import com.ekbotix.ekpayparser.model.Evidence
import com.ekbotix.ekpayparser.protocol.Protocol
import com.ekbotix.ekpayparser.data.QueueItem
import com.ekbotix.ekpayparser.storage.LocalState
import com.ekbotix.ekpayparser.workers.SyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ParserViewModel(application:Application):AndroidViewModel(application){
    private val repository=(application as ParserApplication).repository
    var state by mutableStateOf(LocalState()); private set
    var queue by mutableStateOf<List<QueueItem>>(emptyList()); private set
    var message by mutableStateOf("Welcome. No SMS access. Synthetic sandbox only."); private set
    var busy by mutableStateOf(false); private set
    var protection by mutableStateOf("Not paired"); private set
    init { refresh() }
    fun refresh()=run { state=repository.state.load();queue=repository.queue.all();protection=state.identity?.let {runCatching {repository.keys.protection(it.alias)}.getOrDefault("Key unavailable; re-pair required")}?:"Not paired" }
    private fun run(action:suspend ()->Unit){if(busy)return;busy=true;viewModelScope.launch {try{withContext(Dispatchers.IO){action()}}catch (_:Exception){message="Operation unavailable. Check pairing/key protection/sandbox configuration."}finally{busy=false}}}
    fun pair(device:String,version:String,token:String,clear:()->Unit)=run {
        try { val ok=repository.pair(device.trim().lowercase(),version.toInt(),token.trim());message=if(ok)"Pairing success — TEST only" else "Pairing unavailable — fresh authorized token may be required" }
        finally { withContext(Dispatchers.Main){clear()} };state=repository.state.load();queue=repository.queue.all();protection=state.identity?.let {runCatching {repository.keys.protection(it.alias)}.getOrDefault("Key unavailable")}?:"Not paired"
    }
    fun heartbeat()=run {repository.enqueue(Protocol.HEARTBEAT,Protocol.heartbeatBody(BuildConfig.VERSION_NAME));schedule();state=repository.state.load();queue=repository.queue.all();message="Test heartbeat queued"}
    fun evidence(provider:String,amount:String,transaction:String,timestamp:String,sender:String)=run {
        require(sender.isBlank() || sender.matches(Regex("TEST_[A-Z0-9_-]{1,64}")))
        val fields=Evidence(provider,transaction.trim(),amount.toLong(),timestamp.trim(),"a".repeat(64),sender.trim().takeIf { it.isNotBlank() }?.let { Protocol.sha(it.toByteArray()) })
        repository.enqueue(Protocol.EVIDENCE,Protocol.evidenceBody(fields));schedule();queue=repository.queue.all();message="Synthetic evidence queued with stable ingestion ID"
    }
    private fun schedule(){if(BuildConfig.SANDBOX_NETWORKING)SyncWorker.now(getApplication())}
    fun sync()=run {repository.sync();state=repository.state.load();queue=repository.queue.all();message=state.summary}
    fun settings(consent:Boolean,logging:Boolean)=run {repository.settings(consent,logging);state=repository.state.load()}
    fun stop()=run {repository.markRevoked();state=repository.state.load();queue=repository.queue.all();message="Local terminal stop."}
    fun analyzeSyntheticSms(sender:String,body:String) {
        val result=SmsParserPipeline(listOf(BkashSmsParser(),NagadSmsParser(),RocketSmsParser(),UpaySmsParser()))
            .process(SmsMessageInput(sender,body,System.currentTimeMillis()))
        message="Synthetic in-memory parser status: ${result.status}. No receiver claim or network enqueue."
    }
}
class MainActivity:ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);ReceiverTelemetry.lifecycle("MAIN_ACTIVITY_CREATED",System.currentTimeMillis());window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent {MaterialTheme(colorScheme=lightColorScheme(primary=Color(0xFF157A68),surface=Color(0xFFF5F8F7))){ParserScreen()}}}
}
@Composable private fun ParserScreen(vm:ParserViewModel=viewModel()){
    var screen by remember {mutableStateOf("Welcome")}
    val identity=vm.state.identity;val canSend=identity?.state=="active" && vm.state.pending==null && !vm.busy
    Surface(Modifier.fillMaxSize()) {Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)){
        Text("EkPay Parser",style=MaterialTheme.typography.headlineMedium)
        Text("TEST MODE / SANDBOX",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelLarge)
        Text("Sandbox only · RECEIVE_SMS only · No inbox scan or real payment verification",style=MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("Status","Pair","Evidence").forEach{s->OutlinedButton(onClick={screen=s}){Text(s)}}}
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("Queue","Diagnostics","Settings","SMS Detection").forEach{s->TextButton(onClick={screen=s}){Text(s)}}}
        if(BuildConfig.DEBUG)TextButton(onClick={screen="Format Lab"}){Text("Format Lab (debug)")}
        if(vm.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(vm.message,style=MaterialTheme.typography.bodyMedium)
        if(screen=="Status"||screen=="Diagnostics"){
            Text("Provider: ${identity?.provider?:"Not returned"} · Account: ${identity?.accountDisplay?:"Not returned"}")
            Text("Key protection: ${vm.protection}")
            Text("Paired: ${identity?.pairedAt?.let(Protocol::iso)?:"Not paired"}")
        }
        when(screen){
            "Welcome"->{Text("Welcome to the verification sandbox",style=MaterialTheme.typography.titleLarge);Text("Create a pending synthetic device on the disposable test bridge. Pair with its public ID and one-time token. No production backend is reachable from this app.");Button(onClick={screen="Pair"}){Text("Pair test device")}}
            "Pair"->PairForm(vm)
            "Evidence"->EvidenceForm(vm,canSend)
            "Queue"->{Text("Durable encrypted synthetic queue",style=MaterialTheme.typography.titleLarge);Text("Pending: ${vm.queue.count {it.status=="pending"}} · Max 100 · Retention 24 hours");Button(onClick={vm.sync()},enabled=canSend && BuildConfig.SANDBOX_NETWORKING){Text("Sync test queue")};vm.queue.forEach{q->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Text(q.path.substringAfterLast('/')+" · "+q.status);Text(q.ingestionId,style=MaterialTheme.typography.bodySmall);Text("Attempts: ${q.attempts} · ${q.safeError?:"No error"}")}}}}
            "Diagnostics"->{Text("Safe diagnostics",style=MaterialTheme.typography.titleLarge);Text(vm.state.summary);Text("Last success: ${vm.state.lastSuccess?.let(Protocol::iso)?:"Never"}");Text("Last failure: ${vm.state.lastFailure?.let(Protocol::iso)?:"Never"}");Text("Clock freshness: ±5 minutes. UTC milliseconds at send time. Check the system clock after generic authentication failure; do not spoof server time.");Text("No private keys, pairing tokens, signatures or payload dumps are logged.");OutlinedButton(onClick={vm.refresh()},enabled=!vm.busy){Text("Refresh state")}}
            "Settings"->{Text("Sandbox settings",style=MaterialTheme.typography.titleLarge);Text("Backend: ${BuildConfig.TEST_BASE_URL.ifBlank{"Not configured"}}");Text("Network enabled: ${BuildConfig.DEBUG && BuildConfig.SANDBOX_NETWORKING}");Row{Checkbox(checked=vm.state.softwareConsent,onCheckedChange={vm.settings(it,vm.state.debugLogging)});Text("Explicitly allow Keystore AES-GCM protected software Ed25519 (sandbox only)",Modifier.weight(1f))};Text("Native non-exportable Ed25519 is tried first on API 33+. Fallback is encrypted with a non-exportable Android Keystore AES key, never plaintext; process compromise can expose decrypted software signing material.");Row{Checkbox(checked=vm.state.debugLogging,onCheckedChange={vm.settings(vm.state.softwareConsent,it)});Text("Safe development status logs only")}}
            "SMS Detection"->{
                val context = LocalContext.current
                var hasSmsPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED) }
                var telemetryRefresh by remember { mutableIntStateOf(0) }
                val requestPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasSmsPermission = it }
                Text("SMS Detection",style=MaterialTheme.typography.titleLarge)
                Text("EkPay Parser detects supported payment notification SMS. It ignores unsupported senders, does not send SMS, does not upload unrelated SMS, does not store the full inbox, and sends only normalized payment evidence.")
                if(hasSmsPermission) {
                    Text("SMS Detection is ENABLED.", color=MaterialTheme.colorScheme.primary)
                    var sender by remember{mutableStateOf("TEST_BKASH")}
                    var body by remember{mutableStateOf("You have received Tk 150.00 from 017XXXX. TxnId: TEST_X1Y2Z3")}
                    OutlinedTextField(sender,{sender=it},label={Text("Test Sender")})
                    OutlinedTextField(body,{body=it},label={Text("Test SMS Body")},modifier=Modifier.fillMaxWidth())
                    Button(onClick={vm.analyzeSyntheticSms(sender,body)}){Text("Analyze synthetic sample in memory")}
                    if(BuildConfig.DEBUG){
                        Text("Genuine receiver observation (debug only)",style=MaterialTheme.typography.titleMedium)
                        key(telemetryRefresh){Text(ReceiverTelemetry.summary())}
                        OutlinedButton(onClick={telemetryRefresh++}){Text("Refresh receiver telemetry")}
                    }
                } else {
                    Button(onClick={ requestPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS) }) {
                        Text("Enable SMS Detection")
                    }
                }
            }
            "Format Lab"->{if(BuildConfig.DEBUG)FormatLab() else Text("Format Lab is disabled in release")}
            else->{Text(if(identity==null)"Not paired" else "Device status",style=MaterialTheme.typography.titleLarge);Text("Status: ${identity?.state?:"not paired"}");Text("Public device ID: ${identity?.deviceId?:"Not assigned"}");Text("Environment: TEST · Protocol: 1");Text("Key version: ${identity?.keyVersion?:0} · App: ${BuildConfig.VERSION_NAME}");Text("Provider/account: synthetic backend binding; never caller-selected");Text("Last sync: ${vm.state.lastSuccess?.let(Protocol::iso)?:"Never"}");Text("Pending queue: ${vm.queue.count {it.status=="pending"}}");Button(onClick={vm.heartbeat()},enabled=canSend){Text("Send Test Heartbeat")};OutlinedButton(onClick={vm.stop()},enabled=identity!=null && identity.state!="revoked" && !vm.busy){Text("Stop locally / mark revoked")};Text("Generic 401 pauses every retry; it does not prove remote revocation. Revoked/key-loss identities never automatically reactivate.")}
        }
    }}
}
@Composable private fun FormatLab(){
    var provider by remember{mutableStateOf("bkash")};var sample by remember{mutableStateOf("")};var analysis by remember{mutableStateOf<FormatAnalysis?>(null)}
    Text("Format Lab · DEBUG ONLY",style=MaterialTheme.typography.titleLarge)
    Text("Paste a manually redacted sample. It stays in memory and is not logged, persisted, or uploaded.")
    Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){listOf("bkash","nagad","rocket","upay").forEach { value -> TextButton(onClick={provider=value;analysis=null}){Text(value)} }}
    Text("Provider: $provider · real format state: UNVERIFIED")
    OutlinedTextField(sample,{sample=it;analysis=null},label={Text("Redacted SMS structure")},modifier=Modifier.fillMaxWidth(),minLines=4)
    Button(onClick={analysis=FormatAnalyzer.analyze(provider,sample)},enabled=sample.isNotBlank()){Text("Analyze locally")}
    analysis?.let { result ->
        Text("Status: ${result.status}")
        Text("Amount candidates: ${result.amountCandidates.joinToString().ifBlank{"None"}}")
        Text("Transaction ID candidates: ${result.transactionIdCandidates.joinToString().ifBlank{"None"}}")
        Text("Provider timestamp: ${result.timestampCandidate?:"Not deterministically available"}")
        Text("Keywords: ${result.keywords.joinToString().ifBlank{"None"}}")
        result.warnings.forEach{Text("Warning: $it")}
    }
}
@Composable private fun PairForm(vm:ParserViewModel){
    var device by remember{mutableStateOf("")};var token by remember{mutableStateOf("")};var version by remember{mutableStateOf(vm.state.identity?.keyVersion?.toString()?:"0")}
    Text("Pair / authorized key rotation",style=MaterialTheme.typography.titleLarge)
    OutlinedTextField(device,{device=it},label={Text("EkPay public device UUID")},modifier=Modifier.fillMaxWidth(),singleLine=true)
    OutlinedTextField(version,{version=it},label={Text("Expected current version (0 for first pair)")},singleLine=true)
    OutlinedTextField(token,{token=it},label={Text("One-time pairing token")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth(),singleLine=true)
    Text("Token stays in this screen only and clears after each attempt. Owner/admin re-pairing is the only supported rotation. Candidate key switches only after confirmed server success.")
    Button(onClick={vm.pair(device,version,token){token=""}},enabled=!vm.busy && BuildConfig.DEBUG && BuildConfig.SANDBOX_NETWORKING){Text("Pair synthetic device")}
}
@Composable private fun EvidenceForm(vm:ParserViewModel,enabled:Boolean){
    var provider by remember{mutableStateOf("bkash")};var amount by remember{mutableStateOf("82000")};var transaction by remember{mutableStateOf("TEST_"+Protocol.ingestionId().replace("-","").uppercase())};var timestamp by remember{mutableStateOf(Protocol.iso(System.currentTimeMillis()))};var sender by remember{mutableStateOf("")}
    Text("Synthetic evidence generator",style=MaterialTheme.typography.titleLarge)
    Text("Only test-prefixed references and normalized synthetic facts. No real phone/account numbers or payment content.")
    OutlinedTextField(provider,{provider=it},label={Text("Provider: bkash / nagad / rocket / upay")},singleLine=true)
    OutlinedTextField(amount,{amount=it},label={Text("Positive integer BDT minor units")},singleLine=true)
    OutlinedTextField(transaction,{transaction=it},label={Text("Synthetic transaction ID: TEST_...")},modifier=Modifier.fillMaxWidth())
    OutlinedTextField(timestamp,{timestamp=it},label={Text("Synthetic timestamp UTC ISO milliseconds")},modifier=Modifier.fillMaxWidth())
    OutlinedTextField(sender,{sender=it},label={Text("Optional TEST_ sender reference (hashed)")})
    Button(onClick={vm.evidence(provider,amount,transaction,timestamp,sender)},enabled=enabled){Text("Send Synthetic Evidence")}
}
