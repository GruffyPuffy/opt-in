package se.optin

import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var page: LinearLayout
    private val textSize = 16f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::page.isInitialized) render()
    }

    private fun render() {
        page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        setContentView(ScrollView(this).apply { addView(page) })

        addText("opt-in", 28f, true)
        addText("Choose your home country. Calls from it are always allowed; other countries are blocked unless you allow them.")

        val countries = CallRules.countries
        addText("Home country", 20f, true)
        val homePicker = addCountryPicker(countries, CallRules.homeRegion(this), "Choose your home country…")
        val savedHomeRegion = CallRules.homeRegion(this)
        addButton(if (savedHomeRegion == null) "Set home country" else "Change home country") {
            val country = homePicker()
            if (country == null) {
                Toast.makeText(this, "Select a country first.", Toast.LENGTH_SHORT).show()
            } else {
                CallRules.setHomeRegion(this, country.region)
                render()
            }
        }

        val homeRegion = CallRules.homeRegion(this)
        val homeCountry = countries.firstOrNull { it.region == homeRegion }
        val roleManager = getSystemService(RoleManager::class.java)
        val screeningEnabled = roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) &&
            roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        val screeningButton = addButton(if (screeningEnabled) "Manage call screening" else "Turn on call screening") {
            if (screeningEnabled) {
                startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            } else if (homeRegion != null) {
                startActivityForResult(roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), REQUEST_SCREENING_ROLE)
            }
        }
        screeningButton.isEnabled = homeRegion != null
        if (screeningEnabled) addText("To turn it off, change the call screening app in Default apps settings.", 14f)

        if (homeCountry != null) {
            addText("Allowed countries", 20f, true)
            addRow("${homeCountry.label} · home", "") {}

            val allowedRegions = CallRules.allowedRegions(this) - homeCountry.region
            allowedRegions.mapNotNull { region -> countries.firstOrNull { it.region == region } }
                .sortedBy { it.label }
                .forEach { country ->
                    addRow(country.label, "Remove") {
                        CallRules.removeAllowedRegion(this, country.region)
                        render()
                    }
                }

            val options = countries.filter {
                it.region != homeCountry.region && it.region !in allowedRegions
            }
            addText("Add another allowed country", 20f, true)
            val allowedCountryPicker = addCountryPicker(options, null, "Choose another country…")
            val addCountryButton = addButton("Allow selected country") {
                allowedCountryPicker()?.let { country ->
                    CallRules.addAllowedRegion(this, country.region)
                    render()
                }
            }
            addCountryButton.isEnabled = options.isNotEmpty()

            addText("Allow a specific number", 20f, true)
            val input = EditText(this).apply {
                hint = "Phone number, e.g. +44…"
                inputType = android.text.InputType.TYPE_CLASS_PHONE
            }
            page.addView(input, matchWidth())
            addButton("Add number") {
                if (CallRules.addAllowedNumber(this, input.text.toString())) render()
                else input.error = "Enter a valid phone number"
            }

            addText("Allowed numbers", 20f, true)
            CallRules.allowedNumbers(this).sorted().forEach { number ->
                addRow(number, "Remove") { CallRules.removeAllowedNumber(this, number); render() }
            }
        } else {
            addText("Select and save a home country to turn on call screening.")
        }

        addText("Blocked calls", 20f, true)
        val events = CallRules.blockedCalls(this)
        if (events.isEmpty()) addText("No blocked calls yet.")
        events.forEach { event ->
            val split = event.split('|', limit = 2)
            val number = split.getOrElse(1) { event }
            addRow(number, "Allow number") {
                CallRules.addAllowedNumber(this, number)
                render()
            }
        }
        if (events.isNotEmpty()) addButton("Clear blocked calls") { CallRules.clearBlocked(this); render() }
        addText("Your rules stay on this phone. Calls with no identifiable country are allowed.", 14f)
    }

    private fun addCountryPicker(
        choices: List<Country>,
        selectedRegion: String?,
        placeholder: String
    ): () -> Country? {
        val spinner = Spinner(this)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, listOf(placeholder) + choices.map { it.label })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        var selected = selectedRegion?.let { region -> choices.firstOrNull { it.region == region } }
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                selected = choices.getOrNull(position - 1)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selected = null
            }
        }
        spinner.setSelection(selected?.let { choices.indexOf(it) + 1 } ?: 0)
        page.addView(spinner, matchWidth())
        return { selected }
    }

    private fun addText(value: String, size: Float = textSize, bold: Boolean = false) {
        page.addView(TextView(this).apply {
            text = value
            textSize = size
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 12, 0, 8)
        }, matchWidth())
    }

    private fun addButton(label: String, action: () -> Unit): Button {
        val button = Button(this).apply { text = label; setOnClickListener { action() } }
        page.addView(button, matchWidth())
        return button
    }

    private fun addRow(value: String, actionLabel: String, action: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(TextView(this).apply { text = value; textSize = this@MainActivity.textSize },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (actionLabel.isNotEmpty()) {
            row.addView(Button(this).apply { text = actionLabel; setOnClickListener { action() } })
        }
        page.addView(row, matchWidth())
    }

    private fun matchWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    companion object {
        private const val REQUEST_SCREENING_ROLE = 1
    }
}
