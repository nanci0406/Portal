package moe.fuqiuluo.xposed

import moe.fuqiuluo.xposed.utils.FakeLoc
import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun default_accuracy_is_two_meters() {
        assertEquals(2.0f, FakeLoc.accuracy)
    }
}
