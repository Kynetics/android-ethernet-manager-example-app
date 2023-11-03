/*
 * Copyright (C) 2023 Kynetics, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kynetics.androidethernetexampleapp;

import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        TextView aboutTextView = (TextView) findViewById(R.id.about_text);
        aboutTextView.setMovementMethod(LinkMovementMethod.getInstance());
    }
}
