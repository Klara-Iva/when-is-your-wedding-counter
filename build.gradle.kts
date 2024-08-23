buildscript {

    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }

    dependencies {
        classpath ("com.android.tools.build:gradle:7.4.1")
        classpath("com.github.PhilJay:MPAndroidChart:v3.1.0")
        classpath(libs.google.services)

    }
}

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.jetbrainsKotlinAndroid) apply false
}