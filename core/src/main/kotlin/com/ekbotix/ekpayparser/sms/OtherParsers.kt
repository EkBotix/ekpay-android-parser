package com.ekbotix.ekpayparser.sms

class NagadSmsParser:RuleBasedSmsParser(ProviderRules.nagad)
class RocketSmsParser:RuleBasedSmsParser(ProviderRules.rocket)
class UpaySmsParser:RuleBasedSmsParser(ProviderRules.upay)
