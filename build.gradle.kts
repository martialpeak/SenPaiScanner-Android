plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}

// Force consistent versions across ALL subprojects to prevent dependency conflicts
subprojects {
    configurations.all {
        resolutionStrategy.eachDependency {
            when {
                requested.group == "androidx.legacy" -> useVersion("1.0.0")
                requested.group == "androidx.documentfile" -> useVersion("1.1.0")
                requested.group == "androidx.localbroadcastmanager" -> useVersion("1.1.0")
                requested.group == "androidx.print" -> useVersion("1.1.0")
                requested.group == "androidx.transition" -> useVersion("1.5.0")
                requested.group == "androidx.viewpager2" -> useVersion("1.1.0")
                requested.group == "androidx.dynamicanimation" -> useVersion("1.0.0")
                requested.group == "androidx.recyclerview" -> useVersion("1.3.2")
                requested.group == "androidx.lifecycle" -> useVersion("2.8.3")
            }
        }
    }
}
