package com.example.fires.util

/**
 * The privacy notice and consent wording for sign-up (Data Privacy Act of 2012, RA 10173).
 * The text is plain so it can be copied into Chapter 3 of the paper.
 */
object PrivacyNotice {
    const val CONSENT_LABEL = "I have read the privacy notice and I agree."
    const val READ_LINK = "Read the privacy notice"
    const val MSG_CONSENT_REQUIRED = "Please agree to the privacy notice to create an account."
    const val TITLE = "Privacy notice"
    const val CLOSE = "Close"

    val PARAGRAPHS: List<String> = listOf(
        "What we collect: your name, email, contact number, address, emergency contact, and, when you send a report, your GPS location, the address you confirm, the report details, and a photo taken with the camera.",
        "Why: only to receive your fire report and respond to it. Nothing is used for advertising.",
        "Who can see it: the responders and administrators of Barangay Bagumbayan can read your name, contact number, location and photo. Other citizens cannot read your reports.",
        "AI check: the photo and the written details of a report may be sent to an AI service through the administrator's website. It only gives advice. A person decides everything. Your name and contact number are not sent.",
        "Where it is kept: in the project's Firebase database (Google). Reports are kept for the study and for the barangay's records.",
        "Your rights under RA 10173: you may ask to see, correct or delete your information, and you may withdraw your consent, by contacting the barangay office or the project team."
    )
}
