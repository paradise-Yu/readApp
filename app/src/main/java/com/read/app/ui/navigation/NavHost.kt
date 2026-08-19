package com.read.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.read.app.ui.detail.BookDetailScreen
import com.read.app.ui.folder.FolderScreen
import com.read.app.ui.library.LibraryScreen
import com.read.app.ui.reader.PdfReaderScreen
import com.read.app.ui.reader.TxtReaderScreen
import com.read.app.ui.reader.EpubReaderScreen
import com.read.app.ui.search.SearchScreen

object Routes {
    const val LIBRARY = "library"
    const val SEARCH = "search"
    const val FOLDERS = "folders"
    const val BOOK_DETAIL = "book_detail/{bookId}"
    const val TXT_READER = "txt_reader/{bookId}"
    const val PDF_READER = "pdf_reader/{bookId}"
    const val EPUB_READER = "epub_reader/{bookId}"

    fun bookDetail(bookId: Long) = "book_detail/$bookId"
    fun txtReader(bookId: Long) = "txt_reader/$bookId"
    fun pdfReader(bookId: Long) = "pdf_reader/$bookId"
    fun epubReader(bookId: Long) = "epub_reader/$bookId"
}

@Composable
fun ReadNavHost() {
    val navController = rememberNavController()

    // 监听外部打开文件事件：自动导航到阅读器
    LaunchedEffect(Unit) {
        NavEvents.pendingBook.collect { pending ->
            if (pending != null) {
                when (pending.format.lowercase()) {
                    "txt" -> navController.navigate(Routes.txtReader(pending.bookId))
                    "pdf" -> navController.navigate(Routes.pdfReader(pending.bookId))
                    "epub" -> navController.navigate(Routes.epubReader(pending.bookId))
                }
                NavEvents.clearPendingBook()
            }
        }
    }

    NavHost(navController = navController, startDestination = Routes.LIBRARY) {
        composable(Routes.LIBRARY) {
            LibraryScreen(
                onBookClick = { book ->
                    when (book.format.lowercase()) {
                        "txt" -> navController.navigate(Routes.txtReader(book.id))
                        "pdf" -> navController.navigate(Routes.pdfReader(book.id))
                        "epub" -> navController.navigate(Routes.epubReader(book.id))
                    }
                },
                onBookLongClick = { book ->
                    navController.navigate(Routes.bookDetail(book.id))
                },
                onSearchClick = { navController.navigate(Routes.SEARCH) },
                onFolderClick = { navController.navigate(Routes.FOLDERS) }
            )
        }
        composable(Routes.SEARCH) {
            SearchScreen(
                onBookClick = { book ->
                    when (book.format.lowercase()) {
                        "txt" -> navController.navigate(Routes.txtReader(book.id))
                        "pdf" -> navController.navigate(Routes.pdfReader(book.id))
                        "epub" -> navController.navigate(Routes.epubReader(book.id))
                    }
                },
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Routes.FOLDERS) {
            FolderScreen(onBackClick = { navController.popBackStack() })
        }
        composable(
            Routes.BOOK_DETAIL,
            arguments = listOf(navArgument("bookId") { type = NavType.LongType })
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getLong("bookId") ?: return@composable
            BookDetailScreen(
                bookId = bookId,
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(
            Routes.TXT_READER,
            arguments = listOf(navArgument("bookId") { type = NavType.LongType })
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getLong("bookId") ?: return@composable
            TxtReaderScreen(bookId = bookId, onBackClick = { navController.popBackStack() })
        }
        composable(
            Routes.PDF_READER,
            arguments = listOf(navArgument("bookId") { type = NavType.LongType })
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getLong("bookId") ?: return@composable
            PdfReaderScreen(bookId = bookId, onBackClick = { navController.popBackStack() })
        }
        composable(
            Routes.EPUB_READER,
            arguments = listOf(navArgument("bookId") { type = NavType.LongType })
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getLong("bookId") ?: return@composable
            EpubReaderScreen(bookId = bookId, onBackClick = { navController.popBackStack() })
        }
    }
}
