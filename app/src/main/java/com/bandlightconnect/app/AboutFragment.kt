package com.bandlightconnect.app

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton

class AboutFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_about, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Load version automatically
        val textVersion: TextView = view.findViewById(R.id.textVersion)
        try {
            val pInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            textVersion.text = "Version ${pInfo.versionName}"
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }

        // GitHub Button
        view.findViewById<MaterialButton>(R.id.btnGithub).setOnClickListener {
            openUrl("https://github.com/Dafaol/Band-Trigger")
        }

        // ArtStation Button
        view.findViewById<MaterialButton>(R.id.btnArtstation).setOnClickListener {
            openUrl("https://www.artstation.com/dafaol_creations")
        }

        // Donate Button
        view.findViewById<MaterialButton>(R.id.btnDonate).setOnClickListener {
            openUrl("https://buymeacoffee.com/dafaol")
        }
    }

    // Helper function to open links in the device browser
    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }
}