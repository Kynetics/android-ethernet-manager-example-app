/*
 * Copyright © 2023–2024  Kynetics, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.kynetics.androidethernetexampleapp

import android.os.Bundle
import android.text.method.LinkMovementMethod
import androidx.appcompat.app.AppCompatActivity
import com.kynetics.androidethernetexampleapp.databinding.ActivityAboutBinding

class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        binding.aboutText.movementMethod = LinkMovementMethod.getInstance()
    }
}
