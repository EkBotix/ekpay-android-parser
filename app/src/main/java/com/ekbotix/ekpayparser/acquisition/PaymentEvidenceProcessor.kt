package com.ekbotix.ekpayparser.acquisition

import com.ekbotix.ekpayparser.data.DedupeItem
import com.ekbotix.ekpayparser.model.Evidence
import com.ekbotix.ekpayparser.protocol.Protocol
import com.ekbotix.ekpayparser.repository.ParserRepository
import com.ekbotix.ekpayparser.sms.*

data class ProcessingOutcome(val status:ParseStatus,val queued:Boolean,val provider:String?=null)

class PaymentEvidenceProcessor(
    private val repository:ParserRepository,
    private val registry:InternalProviderRegistry
) {
    private val pipeline=SmsParserPipeline(listOf(BkashSmsParser(),NagadSmsParser(),RocketSmsParser(),UpaySmsParser()))
    suspend fun process(message:AcquiredPaymentMessage):ProcessingOutcome {
        val provider=when(message.source) {
            AcquisitionSource.NOTIFICATION_LISTENER -> registry.packageProvider(message.sourceIdentity)
            AcquisitionSource.SYNTHETIC_TEST -> null
            else -> registry.senderProvider(message.sourceIdentity)
        } ?: return ProcessingOutcome(ParseStatus.UNSUPPORTED_SENDER,false)
        val (result,normalized)=pipeline.normalize(message,provider)
        if(normalized==null)return ProcessingOutcome(result.status,false,result.provider)
        // Protocol v1 requires a provider timestamp. Receipt time is deliberately not substituted.
        val providerTimestamp=normalized.providerTimestamp ?: return ProcessingOutcome(ParseStatus.PARTIAL,false,normalized.provider)
        val evidence=Evidence(normalized.provider,normalized.transactionId,normalized.amountMinor,providerTimestamp,
            receiverHash=null,senderHash=Protocol.sha(message.sourceIdentity.toByteArray(Charsets.UTF_8)))
        val queued=repository.enqueueSms(Protocol.EVIDENCE,Protocol.evidenceBody(evidence),DedupeItem(
            normalized.provider,normalized.transactionId,normalized.messageHash,normalized.localObservedAt,
            normalized.amountMinor,normalized.acquisitionSource.name,normalized.parserVersion
        ))
        if(queued)repository.state.update { it.copy(lastEvidenceAt=normalized.localObservedAt,lastEvidenceProvider=normalized.provider) }
        return ProcessingOutcome(result.status,queued,normalized.provider)
    }
}
