package com.kunzisoft.keepass.view

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.kunzisoft.keepass.R
import com.kunzisoft.keepass.activities.EntryEditActivity
import com.kunzisoft.keepass.activities.IconPickerActivity
import com.kunzisoft.keepass.database.ContextualDatabase
import com.kunzisoft.keepass.database.element.Field
import com.kunzisoft.keepass.database.element.icon.IconImage
import com.kunzisoft.keepass.database.element.icon.IconImageStandard
import com.kunzisoft.keepass.database.element.node.NodeIdUUID
import com.kunzisoft.keepass.database.element.security.ProtectedString
import com.kunzisoft.keepass.database.element.template.Template
import com.kunzisoft.keepass.database.element.template.TemplateBuilder
import com.kunzisoft.keepass.model.EntryInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Builds the entry editor and the icon picker views the same way the screens do,
 * so a broken layout, style or field view fails here instead of crashing the app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EntryEditViewsTest {

    private val themes = listOf(
        R.style.KeepassDXStyle_Light,
        R.style.KeepassDXStyle_Night
    )

    private fun themedContext(theme: Int): Context {
        return ContextThemeWrapper(ApplicationProvider.getApplicationContext(), theme)
    }

    private fun allTemplates(): List<Template> {
        val builder = TemplateBuilder()
        return listOf(
            Template.STANDARD,
            builder.email,
            builder.wifi,
            builder.notes,
            builder.idCard,
            builder.creditCard,
            builder.bank,
            builder.cryptocurrency
        )
    }

    private fun inflate(context: Context, layout: Int): View {
        val parent = FrameLayout(context)
        return LayoutInflater.from(context).inflate(layout, parent, false)
    }

    @Test
    fun entryEditorScreenInflates() {
        themes.forEach { theme ->
            val context = themedContext(theme)
            val fragmentView = inflate(context, R.layout.fragment_entry_edit)
            assertNotNull(fragmentView.findViewById<TemplateEditView>(R.id.template_view))
        }
    }

    @Test
    fun newEntryCanUseEveryTemplate() {
        themes.forEach { theme ->
            val context = themedContext(theme)
            val fragmentView = inflate(context, R.layout.fragment_entry_edit)
            val templateView = fragmentView.findViewById<TemplateEditView>(R.id.template_view)

            var iconPopulated = 0
            templateView.populateIconMethod = { _, _ -> iconPopulated++ }

            // New entry, like EntryEditViewModel does
            templateView.setTemplate(Template.STANDARD)
            templateView.setEntryInfo(EntryInfo())
            allTemplates().forEach { template ->
                templateView.setTemplate(template)
                measure(templateView)
                templateView.getEntryInfo()
            }
            templateView.setTemplate(Template.STANDARD)

            // Change the icon, like after the icon picker returns
            val newIcon = IconImage(IconImageStandard(12))
            templateView.setIcon(newIcon)
            assertEquals(12, templateView.getIcon().standard.id)
            assertEquals(12, templateView.getEntryInfo().icon.standard.id)
            assertTrue(iconPopulated > 0)

            // Custom fields
            val custom = Field("Custom field", ProtectedString(false, "value"))
            assertTrue(templateView.putCustomField(custom))
            val renamed = Field("Custom renamed", ProtectedString(true, "secret"))
            assertTrue(templateView.replaceCustomField(custom, renamed))
            templateView.removeCustomField(renamed)
            templateView.getEntryInfo()
        }
    }

    @Test
    fun existingEntryWithCustomFieldsLoads() {
        themes.forEach { theme ->
            val context = themedContext(theme)
            val fragmentView = inflate(context, R.layout.fragment_entry_edit)
            val templateView = fragmentView.findViewById<TemplateEditView>(R.id.template_view)
            val entryInfo = EntryInfo().apply {
                title = "Title"
                username = "user"
                password = "password".toCharArray()
                url = "https://example.com"
                notes = "notes"
                customFields = mutableListOf(
                    Field("Field A", ProtectedString(false, "a")),
                    Field("Field B", ProtectedString(true, "b"))
                )
            }
            templateView.setTemplate(Template.STANDARD)
            templateView.setEntryInfo(entryInfo)
            measure(templateView)
            val result = templateView.getEntryInfo()
            assertEquals("Title", result.title)
            assertEquals(2, result.customFields.size)
        }
    }

    @Test
    fun iconPickerScreenInflates() {
        themes.forEach { theme ->
            val context = themedContext(theme)
            assertNotNull(inflate(context, R.layout.activity_icon_picker))
            assertNotNull(inflate(context, R.layout.fragment_tabs_pagination))
            assertNotNull(inflate(context, R.layout.fragment_icon_grid))
            assertNotNull(inflate(context, R.layout.item_icon))
        }
    }

    @Test
    fun entryEditActivityLaunchesForNewEntry() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val attachService = com.kunzisoft.keepass.services.AttachmentFileNotificationService()
        val dbService = com.kunzisoft.keepass.services.DatabaseTaskNotificationService()
        shadowOf(app).setComponentNameAndServiceForBindServiceForIntent(
            Intent(app, com.kunzisoft.keepass.services.AttachmentFileNotificationService::class.java),
            android.content.ComponentName(app, com.kunzisoft.keepass.services.AttachmentFileNotificationService::class.java),
            attachService.ActionTaskBinder()
        )
        shadowOf(app).setComponentNameAndServiceForBindServiceForIntent(
            Intent(app, com.kunzisoft.keepass.services.DatabaseTaskNotificationService::class.java),
            android.content.ComponentName(app, com.kunzisoft.keepass.services.DatabaseTaskNotificationService::class.java),
            dbService.ActionTaskBinder()
        )
        val db = ContextualDatabase().apply {
            createData("TestDB", "Root", "Templates")
            loaded = true
        }
        val rootId = db.rootGroup?.nodeId ?: NodeIdUUID()
        val intent = Intent(app, EntryEditActivity::class.java).apply {
            putExtra("parent", rootId)
        }
        val controller = Robolectric.buildActivity(EntryEditActivity::class.java, intent)
            .create()
            .start()
            .resume()
            .visible()
        val activity = controller.get()
        activity.onDatabaseRetrieved(db)
        val fragment = activity.supportFragmentManager.findFragmentById(R.id.entry_edit_content)
            as com.kunzisoft.keepass.activities.fragments.EntryEditFragment
        fragment.onDatabaseRetrieved(db)
        for (i in 0..20) {
            Thread.sleep(50)
            shadowOf(Looper.getMainLooper()).idle()
            if (activity.findViewById<ViewGroup>(R.id.template_title_container).childCount > 0) break
        }
        assertFalse(activity.isFinishing)
        assertTrue(
            "EntryEditFragment did not populate template_title_container",
            activity.findViewById<ViewGroup>(R.id.template_title_container).childCount > 0
        )
        val iconBtn = activity.findViewById<View>(R.id.template_icon_button)
        assertNotNull(iconBtn)
        iconBtn.performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun iconPickerActivityLaunches() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val db = ContextualDatabase().apply {
            createData("TestDB", "Root", "Templates")
            loaded = true
        }
        val intent = Intent(app, IconPickerActivity::class.java).apply {
            putExtra("EXTRA_ICON", IconImage(IconImageStandard(0)))
        }
        val controller = Robolectric.buildActivity(IconPickerActivity::class.java, intent)
            .create()
            .start()
            .resume()
            .visible()
        val activity = controller.get()
        activity.onDatabaseRetrieved(db)
        val pickerFragment = activity.supportFragmentManager.findFragmentById(R.id.icon_picker_fragment)
            as com.kunzisoft.keepass.activities.fragments.IconPickerFragment
        pickerFragment.onDatabaseRetrieved(db)
        shadowOf(Looper.getMainLooper()).idle()
        pickerFragment.view?.let { measure(it) }
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(activity.isFinishing)
    }

    @Test
    @Config(qualifiers = "th")
    fun entryEditActivityLaunchesInThaiLocale() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val attachService = com.kunzisoft.keepass.services.AttachmentFileNotificationService()
        val dbService = com.kunzisoft.keepass.services.DatabaseTaskNotificationService()
        shadowOf(app).setComponentNameAndServiceForBindServiceForIntent(
            Intent(app, com.kunzisoft.keepass.services.AttachmentFileNotificationService::class.java),
            android.content.ComponentName(app, com.kunzisoft.keepass.services.AttachmentFileNotificationService::class.java),
            attachService.ActionTaskBinder()
        )
        shadowOf(app).setComponentNameAndServiceForBindServiceForIntent(
            Intent(app, com.kunzisoft.keepass.services.DatabaseTaskNotificationService::class.java),
            android.content.ComponentName(app, com.kunzisoft.keepass.services.DatabaseTaskNotificationService::class.java),
            dbService.ActionTaskBinder()
        )
        val db = ContextualDatabase().apply {
            createData("TestDB", "Root", "Templates")
            loaded = true
        }
        val rootId = db.rootGroup?.nodeId ?: NodeIdUUID()
        val intent = Intent(app, EntryEditActivity::class.java).apply {
            putExtra("parent", rootId)
        }
        val controller = Robolectric.buildActivity(EntryEditActivity::class.java, intent)
            .create()
            .start()
            .resume()
            .visible()
        val activity = controller.get()
        activity.onDatabaseRetrieved(db)
        val fragment = activity.supportFragmentManager.findFragmentById(R.id.entry_edit_content)
            as com.kunzisoft.keepass.activities.fragments.EntryEditFragment
        fragment.onDatabaseRetrieved(db)
        for (i in 0..20) {
            Thread.sleep(50)
            shadowOf(Looper.getMainLooper()).idle()
            if (activity.findViewById<ViewGroup>(R.id.template_title_container).childCount > 0) break
        }
        assertFalse(activity.isFinishing)
    }

    @Test
    fun iconStandardFragmentLoadsAndBindsIcons() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val db = ContextualDatabase().apply {
            createData("TestDB", "Root", "Templates")
            loaded = true
        }
        val controller = Robolectric.buildActivity(IconPickerActivity::class.java, Intent(app, IconPickerActivity::class.java))
            .create()
            .start()
            .resume()
            .visible()
        val activity = controller.get()
        activity.onDatabaseRetrieved(db)

        val standardFragment = com.kunzisoft.keepass.activities.fragments.IconStandardFragment()
        activity.supportFragmentManager.beginTransaction()
            .add(android.R.id.content, standardFragment)
            .commitNow()
        standardFragment.onDatabaseRetrieved(db)

        for (i in 0..20) {
            Thread.sleep(50)
            shadowOf(Looper.getMainLooper()).idle()
            val rv = standardFragment.view?.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.icons_grid_view)
            if (rv != null && (rv.adapter?.itemCount ?: 0) > 0) {
                measure(rv)
                break
            }
        }
        val rv = standardFragment.view?.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.icons_grid_view)
        assertNotNull(rv)
        assertTrue((rv!!.adapter?.itemCount ?: 0) > 0)
        assertTrue(rv.childCount > 0)
        rv.getChildAt(0).performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun setMainCredentialDialogAndPasswordEditInflate() {
        themes.forEach { theme ->
            val context = themedContext(theme)
            assertNotNull(inflate(context, R.layout.fragment_set_main_credential))
            assertNotNull(inflate(context, R.layout.view_password_edit))
            assertNotNull(inflate(context, R.layout.view_main_credentials))
            assertNotNull(inflate(context, R.layout.activity_main_credential))
        }
    }

    @Test
    fun keyGeneratorActivityLaunches() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val db = ContextualDatabase().apply {
            createData("TestDB", "Root", "Templates")
            loaded = true
        }
        val controller = Robolectric.buildActivity(
            com.kunzisoft.keepass.activities.KeyGeneratorActivity::class.java
        ).create().start().resume().visible()
        val activity = controller.get()
        activity.onDatabaseRetrieved(db)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(activity.isFinishing)
    }

    @Test
    fun templatesSelectorAdapterRendersAllTemplates() {
        val db = ContextualDatabase().apply {
            createData("TestDB", "Root", "Templates")
            loaded = true
        }
        themes.forEach { theme ->
            val context = themedContext(theme)
            val templates = allTemplates()
            val adapter = com.kunzisoft.keepass.adapters.TemplatesSelectorAdapter(context, templates).apply {
                iconDrawableFactory = db.iconDrawableFactory
            }
            val parent = FrameLayout(context)
            for (i in templates.indices) {
                val itemView = adapter.getView(i, null, parent)
                assertNotNull(itemView)
            }
        }
    }

    @Test
    fun entryEditActivityTriggersOptionsMenuAndEducation() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val attachService = com.kunzisoft.keepass.services.AttachmentFileNotificationService()
        val dbService = com.kunzisoft.keepass.services.DatabaseTaskNotificationService()
        shadowOf(app).setComponentNameAndServiceForBindServiceForIntent(
            Intent(app, com.kunzisoft.keepass.services.AttachmentFileNotificationService::class.java),
            android.content.ComponentName(app, com.kunzisoft.keepass.services.AttachmentFileNotificationService::class.java),
            attachService.ActionTaskBinder()
        )
        shadowOf(app).setComponentNameAndServiceForBindServiceForIntent(
            Intent(app, com.kunzisoft.keepass.services.DatabaseTaskNotificationService::class.java),
            android.content.ComponentName(app, com.kunzisoft.keepass.services.DatabaseTaskNotificationService::class.java),
            dbService.ActionTaskBinder()
        )
        val db = ContextualDatabase().apply {
            createData("TestDB", "Root", "Templates")
            loaded = true
        }
        val rootId = db.rootGroup?.nodeId ?: NodeIdUUID()
        val intent = Intent(app, EntryEditActivity::class.java).apply {
            putExtra("parent", rootId)
        }
        val controller = Robolectric.buildActivity(EntryEditActivity::class.java, intent)
            .create()
            .start()
            .resume()
            .visible()
        val activity = controller.get()
        activity.onDatabaseRetrieved(db)
        val fragment = activity.supportFragmentManager.findFragmentById(R.id.entry_edit_content)
            as com.kunzisoft.keepass.activities.fragments.EntryEditFragment
        fragment.onDatabaseRetrieved(db)
        for (i in 0..20) {
            Thread.sleep(50)
            shadowOf(Looper.getMainLooper()).idle()
            if (activity.findViewById<ViewGroup>(R.id.template_title_container).childCount > 0) break
        }
        val menu = org.robolectric.fakes.RoboMenu(activity)
        activity.onCreateOptionsMenu(menu)
        activity.onPrepareOptionsMenu(menu)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(activity.isFinishing)
    }

    private fun measure(view: View) {
        val spec = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.layoutParams = view.layoutParams
            ?: ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        view.measure(spec, heightSpec)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }
}
