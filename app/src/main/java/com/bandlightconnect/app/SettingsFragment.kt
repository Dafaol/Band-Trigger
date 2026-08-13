package com.bandlightconnect.app

import android.provider.Settings
import android.os.Handler
import android.os.Looper
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsFragment : Fragment() {

    private lateinit var switchHijackFocus: SwitchMaterial

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        switchHijackFocus = view.findViewById(R.id.switchHijackFocus)

        // Load saved state
        val sharedPrefs = requireActivity().getSharedPreferences("BandTriggerPrefs", Context.MODE_PRIVATE)
        val isCurrentlyEnabled = sharedPrefs.getBoolean("AUTO_FOCUS_ENABLED", false)
        switchHijackFocus.isChecked = isCurrentlyEnabled

        // Handle Switch clicks
        switchHijackFocus.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!isNotificationServiceEnabled()) {
                    // Revert switch visually until permission is actually granted
                    switchHijackFocus.isChecked = false
                    showPermissionDialog()
                } else {
                    sharedPrefs.edit().putBoolean("AUTO_FOCUS_ENABLED", true).apply()
                    Toast.makeText(requireContext(), "Hijack Band Focus Enabled", Toast.LENGTH_SHORT).show()
                }
            } else {
                sharedPrefs.edit().putBoolean("AUTO_FOCUS_ENABLED", false).apply()
                Toast.makeText(requireContext(), "Hijack Band Focus Disabled", Toast.LENGTH_SHORT).show()
            }
        }

        // --- Hardware Controls (Camera Shutter) ---
        val switchCameraShutter = view.findViewById<SwitchMaterial>(R.id.switchCameraShutter)

        // Se o serviço estiver ativado nas configurações do Android, o switch já deve começar ligado.
        switchCameraShutter.isChecked = RemoteCameraService.instance != null

        switchCameraShutter.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Se o usuário ligar o switch mas o serviço não estiver ativado no sistema:
                if (RemoteCameraService.instance == null) {
                    // Avisa o usuário e abre a tela de acessibilidade do Android direto!
                    Toast.makeText(context, "Ative o Band Trigger para usar a câmera", Toast.LENGTH_LONG).show()
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    switchCameraShutter.isChecked = false // Mantém desligado até ele voltar com a permissão
                } else {
                    // --- TESTE DE FUNCIONALIDADE (Temporário para desenvolvimento) ---
                    // Se estiver ativado, vamos testar o "click" agora para provar que funciona.
                    // Em produção, isso seria chamado no NotificationListener.
                    Toast.makeText(context, "Testing Remote Shutter in 3 seconds. Open your camera!", Toast.LENGTH_SHORT).show()
                    Handler(Looper.getMainLooper()).postDelayed({
                        RemoteCameraService.instance?.triggerCameraShutter()
                    }, 3000)
                }
            } else {
                // Se ele desligar o switch, desativar no sistema não é fácil.
                // O melhor é apenas "ignorar" o comando no código se o switch estiver off.
                Toast.makeText(context, "Feature disabled.", Toast.LENGTH_SHORT).show()
            }
        }
    }


    // Refresh state when coming back from Android Settings
    override fun onResume() {
        super.onResume()
        val sharedPrefs = requireActivity().getSharedPreferences("BandTriggerPrefs", Context.MODE_PRIVATE)
        val isCurrentlyEnabled = sharedPrefs.getBoolean("AUTO_FOCUS_ENABLED", false)

        if (isCurrentlyEnabled && !isNotificationServiceEnabled()) {
            sharedPrefs.edit().putBoolean("AUTO_FOCUS_ENABLED", false).apply()
            switchHijackFocus.isChecked = false
        } else {
            switchHijackFocus.isChecked = isCurrentlyEnabled
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val pkgName = requireContext().packageName
        val flat = android.provider.Settings.Secure.getString(requireContext().contentResolver, "enabled_notification_listeners")
        return flat?.contains(pkgName) == true
    }

    private fun showPermissionDialog() {
        val dialog = AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Permission Required")
            .setMessage("To use 'Hijack Band Focus', the app needs Notification Access.\n\nNote: This allows the app to intercept media pause events.")
            .setPositiveButton("Grant Access") { _, _ ->
                startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.WHITE)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.WHITE)
        }
        dialog.show()
    }
}