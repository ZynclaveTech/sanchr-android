package com.sanchr.domain.contacts

import android.content.ContentResolver
import android.content.Context
import android.provider.ContactsContract
import com.sanchr.core.common.DispatcherProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.withContext

/**
 * Finds which of the people in this phone's address book are on Sanchr.
 *
 * Reads the address book and hands it to [ContactRepository.discoverAndSyncContacts],
 * which does the finding without the server seeing it. This is the whole of
 * "sync contacts", in one place, so that every screen offering it does the
 * same thing — onboarding offered it and did none of it.
 *
 * The caller must hold `READ_CONTACTS`. Without it the query returns nothing
 * and nobody is found.
 *
 * @return the number of registered contacts found.
 */
class DiscoverDeviceContactsUseCase
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val contactRepository: ContactRepository,
        private val dispatchers: DispatcherProvider,
    ) {
        suspend operator fun invoke(): Int =
            withContext(dispatchers.io) {
                contactRepository.discoverAndSyncContacts(DeviceAddressBook.read(context.contentResolver))
            }
    }

/** The phone's own address book. */
object DeviceAddressBook {
    /**
     * Raw numbers as stored, each once, with the name of the entry it belongs
     * to. Normalising happens in the repository; the name never leaves the
     * device.
     */
    fun read(contentResolver: ContentResolver): List<DeviceContact> {
        val seen = mutableSetOf<String>()
        val contacts = mutableListOf<DeviceContact>()
        val cursor =
            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ),
                null,
                null,
                null,
            )
        cursor?.use {
            val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            while (it.moveToNext()) {
                val number = it.getString(numberIndex)
                if (number.isNullOrBlank() || !seen.add(number)) continue
                contacts += DeviceContact(rawNumber = number, name = it.getString(nameIndex)?.takeIf { n -> n.isNotBlank() })
            }
        }
        return contacts
    }
}
