package com.unofficial.kodakremote

import android.graphics.Color as AColor
import android.os.Bundle
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.setContent

enum class Screen { Onboarding, Remote, Connect, Compat, Ir, Settings }

class MainActivity : AppCompatActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { vm.settings.value == null }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT)
        )
        setContent { KodakTheme { App(vm) } }
    }
}

@Composable
fun App(vm: AppViewModel) {
    val ctx = LocalContext.current
    val loaded by vm.settings.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf<Screen?>(null) }

    LaunchedEffect(Unit) {
        vm.messages.collect { Toast.makeText(ctx, ctx.getString(it), Toast.LENGTH_SHORT).show() }
    }

    val s = loaded
    if (s == null) {
        Box(Modifier.fillMaxSize().background(Bg))
        return
    }
    val current = screen ?: if (!s.onboarded) Screen.Onboarding else Screen.Remote

    BackHandler(enabled = current != Screen.Remote && current != Screen.Onboarding) { screen = Screen.Remote }

    CompositionLocalProvider(LocalHaptics provides s.haptics) {
        when (current) {
            Screen.Onboarding -> OnboardingScreen(onFinish = {
                vm.finishOnboarding()
                screen = Screen.Compat
            })
            Screen.Remote -> RemoteScreen(
                vm, s,
                onConnect = { screen = Screen.Connect },
                onIr = { screen = Screen.Ir },
                onSettings = { screen = Screen.Settings },
                onCompat = { screen = Screen.Compat },
            )
            Screen.Connect -> ConnectScreen(vm, onDone = { screen = Screen.Remote }, onBack = { screen = Screen.Remote })
            Screen.Compat -> CompatScreen(
                vm, onBack = { screen = Screen.Remote },
                onPair = { screen = Screen.Connect },
                onUseIr = { vm.setMode("ir"); screen = Screen.Ir },
            )
            Screen.Ir -> IrScreen(vm, s, onBack = { screen = Screen.Remote })
            Screen.Settings -> SettingsScreen(vm, s, onBack = { screen = Screen.Remote }, onCompat = { screen = Screen.Compat })
        }
    }
}
