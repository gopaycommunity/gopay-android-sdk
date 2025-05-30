package com.gopay.sdk.internal

import android.content.Context
import com.gopay.sdk.exception.GopaySDKException
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Unit tests for GopayContextProvider
 */
class GopayContextProviderTest {

    @After
    fun tearDown() {
        // Clear context after each test
        GopayContextProvider.clearContext()
    }

    @Test
    fun `setApplicationContext should store context correctly`() {
        // Given a mock context
        val mockContext = mock<Context>()
        val mockAppContext = mock<Context>()
        whenever(mockContext.applicationContext).thenReturn(mockAppContext)

        // When setting the application context
        GopayContextProvider.setApplicationContext(mockContext)

        // Then context should be available
        assertTrue(GopayContextProvider.isContextAvailable())
        assertEquals(mockAppContext, GopayContextProvider.getApplicationContext())
    }

    @Test
    fun `getApplicationContext should throw exception when no context available`() {
        // Given no context is set
        // (context is cleared in tearDown)

        // When getting application context
        // Then a GopaySDKException should be thrown
        val exception = assertThrows(GopaySDKException::class.java) {
            GopayContextProvider.getApplicationContext()
        }

        assertTrue("Exception should mention context not available", 
            exception.message!!.contains("Cannot obtain Application context"))
    }

    @Test
    fun `isContextAvailable should return false when no context set`() {
        // Given no context is set
        // (context is cleared in tearDown)

        // When checking if context is available
        val isAvailable = GopayContextProvider.isContextAvailable()

        // Then it should return false
        assertFalse(isAvailable)
    }

    @Test
    fun `isContextAvailable should return true when context is set`() {
        // Given a mock context is set
        val mockContext = mock<Context>()
        whenever(mockContext.applicationContext).thenReturn(mockContext)
        GopayContextProvider.setApplicationContext(mockContext)

        // When checking if context is available
        val isAvailable = GopayContextProvider.isContextAvailable()

        // Then it should return true
        assertTrue(isAvailable)
    }

    @Test
    fun `clearContext should remove stored context`() {
        // Given a context is set
        val mockContext = mock<Context>()
        whenever(mockContext.applicationContext).thenReturn(mockContext)
        GopayContextProvider.setApplicationContext(mockContext)
        assertTrue(GopayContextProvider.isContextAvailable())

        // When clearing the context
        GopayContextProvider.clearContext()

        // Then context should no longer be available
        assertFalse(GopayContextProvider.isContextAvailable())
    }

    @Test
    fun `setApplicationContext should always use applicationContext`() {
        // Given a mock context with different applicationContext
        val mockContext = mock<Context>()
        val mockAppContext = mock<Context>()
        whenever(mockContext.applicationContext).thenReturn(mockAppContext)

        // When setting the context
        GopayContextProvider.setApplicationContext(mockContext)

        // Then the stored context should be the applicationContext, not the original
        assertEquals(mockAppContext, GopayContextProvider.getApplicationContext())
    }
} 