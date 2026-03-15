package org.jellyfin.androidtv.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.CollectionType
import timber.log.Timber

interface UserViewsRepository {
	val views: Flow<Collection<BaseItemDto>>
	val allViews: Flow<Collection<BaseItemDto>>

	fun isSupported(collectionType: CollectionType?): Boolean
	fun allowViewSelection(collectionType: CollectionType?): Boolean
	fun allowGridView(collectionType: CollectionType?): Boolean
}

class UserViewsRepositoryImpl(
	private val api: ApiClient,
	private val sessionRepository: SessionRepository,
	private val userRepository: UserRepository,
) : UserViewsRepository {
	private val scope = CoroutineScope(Dispatchers.IO)

	// Re-fetch views whenever the active user changes
	private val sessionChange = sessionRepository.currentSession
		.filterNotNull()
		.distinctUntilChangedBy { it.userId }

	override val views: Flow<Collection<BaseItemDto>> = sessionChange
		.flatMapLatest {
			flow {
				try {
					val views by api.userViewsApi.getUserViews(includeHidden = false)
					emit(views.items.filter { isSupported(it.collectionType) })
				} catch (err: Exception) {
					Timber.e(err, "Failed to get user views")
					emit(emptyList())
				}
			}.flowOn(Dispatchers.IO)
		}
		.combine(userRepository.currentUser) { views, user ->
			val excludes = user?.configuration?.myMediaExcludes.orEmpty().toSet()
			if (excludes.isEmpty()) views
			else views.filter { it.id !in excludes }
		}
		.shareIn(scope, SharingStarted.Lazily, replay = 1)

	override val allViews: Flow<Collection<BaseItemDto>> = sessionChange
		.flatMapLatest {
			flow {
				try {
					val views by api.userViewsApi.getUserViews(includeHidden = true)
					val filteredViews = views.items
						.filter { isSupported(it.collectionType) }
					emit(filteredViews)
				} catch (err: Exception) {
					Timber.e(err, "Failed to get all user views")
					emit(emptyList())
				}
			}
		}.flowOn(Dispatchers.IO).shareIn(scope, SharingStarted.Lazily, replay = 1)

	override fun isSupported(collectionType: CollectionType?) = collectionType !in unsupportedCollectionTypes
	override fun allowViewSelection(collectionType: CollectionType?) = collectionType !in disallowViewSelectionCollectionTypes
	override fun allowGridView(collectionType: CollectionType?) = collectionType !in disallowGridViewCollectionTypes

	private companion object {
		private val unsupportedCollectionTypes = arrayOf(
			CollectionType.BOOKS,
			CollectionType.FOLDERS
		)

		private val disallowViewSelectionCollectionTypes = arrayOf(
			CollectionType.LIVETV,
			CollectionType.MUSIC,
			CollectionType.PHOTOS,
		)

		private val disallowGridViewCollectionTypes = arrayOf(
			CollectionType.LIVETV,
			CollectionType.MUSIC
		)
	}
}
