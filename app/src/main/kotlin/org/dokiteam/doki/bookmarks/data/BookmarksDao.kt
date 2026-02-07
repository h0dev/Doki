package org.dokiteam.doki.bookmarks.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import org.dokiteam.doki.core.db.entity.MangaWithTags

@Dao
abstract class BookmarksDao {

	@Query("SELECT * FROM bookmarks WHERE page_id = :pageId")
	abstract suspend fun find(pageId: Long): BookmarkEntity?

	@Transaction
	@Query(
		"SELECT * FROM manga JOIN bookmarks ON bookmarks.manga_id = manga.manga_id ORDER BY percent LIMIT :limit OFFSET :offset",
	)
	abstract suspend fun findAll(offset: Int, limit: Int): Map<MangaWithTags, List<BookmarkEntity>>

	@Query("SELECT * FROM bookmarks WHERE manga_id = :mangaId AND chapter_id = :chapterId AND page = :page ORDER BY percent")
	abstract fun observe(mangaId: Long, chapterId: Long, page: Int): Flow<BookmarkEntity?>

	@Query("SELECT * FROM bookmarks WHERE manga_id = :mangaId ORDER BY percent")
	abstract fun observe(mangaId: Long): Flow<List<BookmarkEntity>>

	@Query("SELECT COUNT(*) FROM bookmarks WHERE updated_at > :since")
	abstract suspend fun countChanges(since: Long): Int

	@Transaction
	@Query(
		"SELECT * FROM manga JOIN bookmarks ON bookmarks.manga_id = manga.manga_id ORDER BY percent",
	)
	abstract fun observe(): Flow<Map<MangaWithTags, List<BookmarkEntity>>>

	@Insert
	abstract suspend fun insert(entity: BookmarkEntity)

	@Delete
	abstract suspend fun delete(entity: BookmarkEntity)

	@Query("DELETE FROM bookmarks WHERE page_id = :pageId")
	abstract suspend fun delete(pageId: Long): Int

	@Query("DELETE FROM bookmarks WHERE manga_id = :mangaId AND chapter_id = :chapterId AND page = :page")
	abstract suspend fun delete(mangaId: Long, chapterId: Long, page: Int): Int

	@Query("UPDATE bookmarks SET chapter_id = :chapterId, page = :page, scroll = :scroll, image = :imageUrl, percent = :percent, updated_at = :updatedAt WHERE manga_id = :mangaId AND page_id = :pageId")
	abstract suspend fun update(mangaId: Long, pageId: Long, chapterId: Long, page: Int, scroll: Int, imageUrl: String, percent: Float, updatedAt: Long): Int

	@Transaction
	open suspend fun upsert(bookmarks: Collection<BookmarkEntity>) {
		for (bookmark in bookmarks) {
			val bookmarkWithTimestamp = bookmark.copy(updatedAt = System.currentTimeMillis())
			val updatedRows = update(
				bookmarkWithTimestamp.mangaId,
				bookmarkWithTimestamp.pageId,
				bookmarkWithTimestamp.chapterId,
				bookmarkWithTimestamp.page,
				bookmarkWithTimestamp.scroll,
				bookmarkWithTimestamp.imageUrl,
				bookmarkWithTimestamp.percent,
				bookmarkWithTimestamp.updatedAt
			)
			if (updatedRows == 0) {
				insert(bookmarkWithTimestamp)
			}
		}
	}


	fun dump(): Flow<Pair<MangaWithTags, List<BookmarkEntity>>> = flow {
		val window = 4
		var offset = 0
		while (currentCoroutineContext().isActive) {
			val list = findAll(offset, window)
			if (list.isEmpty()) {
				break
			}
			offset += window
			list.forEach { emit(it.key to it.value) }
		}
	}
}
