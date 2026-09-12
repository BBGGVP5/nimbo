package com.danila.nimbo.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidInteractionPolicyTest {

    @Test
    fun `released surface keeps identity transform`() {
        val transform = LiquidInteractionPolicy.pressTransform(
            pressed = false,
            width = 200f,
            height = 100f,
            touchX = 180f,
            touchY = 20f,
            intensity = 1f
        )

        assertEquals(LiquidPressTransform.Identity, transform)
    }

    @Test
    fun `center press expands without translation`() {
        val transform = LiquidInteractionPolicy.pressTransform(
            pressed = true,
            width = 200f,
            height = 100f,
            touchX = 100f,
            touchY = 50f,
            intensity = 1f
        )

        assertTrue(transform.scaleX > 1f)
        assertTrue(transform.scaleY > 1f)
        assertEquals(0f, transform.translationX, 0.001f)
        assertEquals(0f, transform.translationY, 0.001f)
        assertEquals(0.5f, transform.pivotX, 0.001f)
        assertEquals(0.5f, transform.pivotY, 0.001f)
    }

    @Test
    fun `edge press stretches and moves toward touch`() {
        val transform = LiquidInteractionPolicy.pressTransform(
            pressed = true,
            width = 200f,
            height = 100f,
            touchX = 200f,
            touchY = 50f,
            intensity = 1f
        )

        assertTrue(transform.scaleX > transform.scaleY)
        assertTrue(transform.translationX > 0f)
        assertTrue(transform.pivotX < 0.5f)
    }

    @Test
    fun `surface keeps stretching while finger moves beyond horizontal edge`() {
        val edge = LiquidInteractionPolicy.pressTransform(
            pressed = true,
            width = 200f,
            height = 100f,
            touchX = 200f,
            touchY = 50f,
            intensity = 1f
        )
        val outside = LiquidInteractionPolicy.pressTransform(
            pressed = true,
            width = 200f,
            height = 100f,
            touchX = 300f,
            touchY = 50f,
            intensity = 1f
        )

        assertTrue(outside.scaleX > edge.scaleX)
        assertTrue(outside.translationX > edge.translationX)
    }

    @Test
    fun `elastic resistance grows more slowly after crossing the edge`() {
        val halfway = LiquidInteractionPolicy.pressTransform(
            pressed = true,
            width = 200f,
            height = 100f,
            touchX = 150f,
            touchY = 50f,
            intensity = 1f
        )
        val edge = LiquidInteractionPolicy.pressTransform(
            pressed = true,
            width = 200f,
            height = 100f,
            touchX = 200f,
            touchY = 50f,
            intensity = 1f
        )
        val outside = LiquidInteractionPolicy.pressTransform(
            pressed = true,
            width = 200f,
            height = 100f,
            touchX = 300f,
            touchY = 50f,
            intensity = 1f
        )

        val insideGrowth = edge.scaleX - halfway.scaleX
        val outsideGrowth = outside.scaleX - edge.scaleX
        assertTrue(outsideGrowth > 0f)
        assertTrue("inside=$insideGrowth outside=$outsideGrowth", outsideGrowth < insideGrowth)
    }

    @Test
    fun `horizontal movement stays elastic while vertical scrolling cancels it`() {
        assertEquals(
            false,
            LiquidInteractionPolicy.shouldCancelForScroll(deltaX = 32f, deltaY = 5f, touchSlop = 8f)
        )
        assertEquals(
            true,
            LiquidInteractionPolicy.shouldCancelForScroll(deltaX = 5f, deltaY = 32f, touchSlop = 8f)
        )
        assertEquals(
            false,
            LiquidInteractionPolicy.shouldCancelForScroll(deltaX = 4f, deltaY = 5f, touchSlop = 8f)
        )
    }

    @Test
    fun `control deformation is stronger than panel deformation`() {
        val panel = LiquidInteractionPolicy.pressTransform(
            pressed = true,
            width = 200f,
            height = 100f,
            touchX = 180f,
            touchY = 50f,
            intensity = 0.35f
        )
        val control = LiquidInteractionPolicy.pressTransform(
            pressed = true,
            width = 200f,
            height = 100f,
            touchX = 180f,
            touchY = 50f,
            intensity = 1f
        )

        assertTrue(control.scaleX > panel.scaleX)
        assertTrue(control.translationX > panel.translationX)
    }

    @Test
    fun `tab drag maps item centers to continuous indices`() {
        assertEquals(0f, LiquidInteractionPolicy.continuousTabIndex(50f, 400f, 4), 0.001f)
        assertEquals(1f, LiquidInteractionPolicy.continuousTabIndex(150f, 400f, 4), 0.001f)
        assertEquals(3f, LiquidInteractionPolicy.continuousTabIndex(350f, 400f, 4), 0.001f)
    }

    @Test
    fun `tab drag clamps outside bar and selects nearest item`() {
        assertEquals(0f, LiquidInteractionPolicy.continuousTabIndex(-20f, 400f, 4), 0.001f)
        assertEquals(3f, LiquidInteractionPolicy.continuousTabIndex(500f, 400f, 4), 0.001f)
        assertEquals(2, LiquidInteractionPolicy.nearestTabIndex(2.49f, 4))
        assertEquals(3, LiquidInteractionPolicy.nearestTabIndex(2.51f, 4))
    }

    @Test
    fun `bubble stretches most between two tabs`() {
        assertEquals(0f, LiquidInteractionPolicy.bubbleStretch(2f), 0.001f)
        assertEquals(1f, LiquidInteractionPolicy.bubbleStretch(2.5f), 0.001f)
    }

    @Test
    fun `landing keeps the nearest tab even after a fast fling`() {
        assertEquals(1, LiquidInteractionPolicy.landingTargetIndex(1.40f, 12f, 4))
        assertEquals(1, LiquidInteractionPolicy.landingTargetIndex(1.40f, -12f, 4))
        assertEquals(2, LiquidInteractionPolicy.landingTargetIndex(1.60f, -12f, 4))
    }

    @Test
    fun `landing velocity is converted to tabs per second and clamped`() {
        assertEquals(2f, LiquidInteractionPolicy.landingVelocity(200f, 100f), 0.001f)
        assertEquals(3.2f, LiquidInteractionPolicy.landingVelocity(2_000f, 100f), 0.001f)
        assertEquals(-3.2f, LiquidInteractionPolicy.landingVelocity(-2_000f, 100f), 0.001f)
        assertEquals(0f, LiquidInteractionPolicy.landingVelocity(500f, 0f), 0.001f)
    }

    @Test
    fun `faster and longer landing creates a stronger bounded plop`() {
        val slow = LiquidInteractionPolicy.landingImpact(0.2f, 0.1f)
        val fast = LiquidInteractionPolicy.landingImpact(3f, 0.45f)

        assertTrue(fast > slow)
        assertTrue(slow >= 0.55f)
        assertTrue(fast <= 1f)
    }

    @Test
    fun `landing delay follows remaining distance within a short bound`() {
        assertEquals(70L, LiquidInteractionPolicy.landingDelayMillis(0f))
        assertEquals(130L, LiquidInteractionPolicy.landingDelayMillis(0.5f))
        assertEquals(190L, LiquidInteractionPolicy.landingDelayMillis(1f))
        assertEquals(190L, LiquidInteractionPolicy.landingDelayMillis(4f))
    }
}
