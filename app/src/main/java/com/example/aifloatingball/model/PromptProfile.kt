package com.example.aifloatingball.model

import java.util.UUID

data class PromptProfile(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "默认档案",
    var isSelected: Boolean = false,

    // Basic Info
    var gender: String = "unspecified",
    var birthDate: String = "",
    var education: String = "",

    // Occupation
    var occupation: String = "",
    var occupationCurrent: Set<String> = emptySet(),
    var occupationInterest: Set<String> = emptySet(),

    // Interests
    var interests: Set<String> = emptySet(),
    var interestsEntertainment: Set<String> = emptySet(),
    var interestsShopping: Set<String> = emptySet(),
    var interestsNiche: Set<String> = emptySet(),
    var interestsOrientation: String = "decline_to_state",
    var interestsValues: Set<String> = emptySet(),

    // Health
    var health: Set<String> = emptySet(),
    var healthDiet: Set<String> = emptySet(),
    var healthChronic: Set<String> = emptySet(),
    var healthPhysicalState: String = "",
    var healthMedication: String = "",
    var healthConstitution: Set<String> = emptySet(),
    var healthMedicalPref: String = "integrated",
    var healthHabits: String = "",
    var healthDiagnosed: Set<String> = emptySet(),
    var healthHadSurgery: Boolean = false,
    var healthSurgeryType: Set<String> = emptySet(),
    var healthSurgeryTime: String = "",
    var healthHasAllergies: Boolean = false,
    var healthAllergyCause: Set<String> = emptySet(),
    var healthAllergyHistory: Set<String> = emptySet(),
    var healthFamilyHistory: Set<String> = emptySet(),
    var healthDietaryRestrictions: Set<String> = emptySet(),
    var healthSleepPattern: String = "",

    // Reply Preferences
    var replyFormats: Set<String> = emptySet(),
    var refusedTopics: Set<String> = emptySet(),
    var toneStyle: String = "professional"
) 