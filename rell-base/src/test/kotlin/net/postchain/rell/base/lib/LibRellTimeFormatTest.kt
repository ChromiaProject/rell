package net.postchain.rell.base.lib

import net.postchain.rell.base.testutils.BaseRellTest
import kotlin.test.Test

class LibRellTimeFormatTest: BaseRellTest() {
    @Test fun testForbiddenFormatCharsConstructor() {
        chk("rell.time.format('G')", "rt_err:rell.time.format:format_text:illegal_format_character:G")
        chk("rell.time.format('Y')", "rt_err:rell.time.format:format_text:illegal_format_character:Y")
        chk("rell.time.format('L')", "rt_err:rell.time.format:format_text:illegal_format_character:L")
        chk("rell.time.format('F')", "rt_err:rell.time.format:format_text:illegal_format_character:F")
        chk("rell.time.format('k')", "rt_err:rell.time.format:format_text:illegal_format_character:k")
        chk("rell.time.format('K')", "rt_err:rell.time.format:format_text:illegal_format_character:K")
        chk("rell.time.format('z')", "rt_err:rell.time.format:format_text:illegal_format_character:z")
        chk("rell.time.format('Z')", "rt_err:rell.time.format:format_text:illegal_format_character:Z")
        chk("rell.time.format('X')", "rt_err:rell.time.format:format_text:illegal_format_character:X")
    }

    @Test fun testUnicodeIllegal() {
        chk("rell.time.format('➴')", "rt_err:rell.time.format:format_text:illegal_format_character:➴")
        chk("rell.time.format('\\'Țuică\\'')", "rt_err:rell.time.format:format_text:illegal_character:Ț")
    }

    @Test fun testForbiddenFormatCharsOkayQuoted() {
        chk("rell.time.format('\\'G\\'')", "rell.time.format['G']")
        chk("rell.time.format('\\'Y\\'')", "rell.time.format['Y']")
        chk("rell.time.format('\\'L\\'')", "rell.time.format['L']")
        chk("rell.time.format('\\'F\\'')", "rell.time.format['F']")
        chk("rell.time.format('\\'k\\'')", "rell.time.format['k']")
        chk("rell.time.format('\\'K\\'')", "rell.time.format['K']")
        chk("rell.time.format('\\'z\\'')", "rell.time.format['z']")
        chk("rell.time.format('\\'Z\\'')", "rell.time.format['Z']")
        chk("rell.time.format('\\'X\\'')", "rell.time.format['X']")
    }

    @Test fun testConstructor() {
        chk("rell.time.format('yyyy.MM.dd \\'at\\' HH:mm:ss')", "rell.time.format[yyyy.MM.dd 'at' HH:mm:ss]")
        chk("rell.time.format('EEE, MMM d, \\'\\'yy')", "rell.time.format[EEE, MMM d, ''yy]")
        chk("rell.time.format('h:mm a')", "rell.time.format[h:mm a]")
        chk("rell.time.format('hh \\'o\\'\\'clock\\' a')", "rell.time.format[hh 'o''clock' a]")
        chk("rell.time.format('hh:mm a')", "rell.time.format[hh:mm a]")
        chk("rell.time.format('yyyyy.MMMMM.dd hh:mm aaa')", "rell.time.format[yyyyy.MMMMM.dd hh:mm aaa]")
        chk("rell.time.format('EEE, d MMM yyyy HH:mm:ss')", "rell.time.format[EEE, d MMM yyyy HH:mm:ss]")
        chk("rell.time.format('yyMMddHHmmss')", "rell.time.format[yyMMddHHmmss]")
        chk("rell.time.format('yyyy-MM-dd\\'T\\'HH:mm:ss.SSS')", "rell.time.format[yyyy-MM-dd'T'HH:mm:ss.SSS]")
        chk("rell.time.format('yyyy-\\'W\\'ww-u')", "rell.time.format[yyyy-'W'ww-u]")
    }

    @Test fun testMsToText() {
        val ms = "994244936235"
        chk("rell.time.format('yyyy.MM.dd \\'at\\' HH:mm:ss').ms_to_text($ms)", "text[2001.07.04 at 11:08:56]")
        chk("rell.time.format('EEE, MMM d, \\'\\'yy').ms_to_text($ms)", "text[Wed, Jul 4, '01]")
        chk("rell.time.format('h:mm a').ms_to_text($ms)", "text[11:08 AM]")
        chk("rell.time.format('hh \\'o\\'\\'clock\\' a').ms_to_text($ms)", "text[11 o'clock AM]")
        chk("rell.time.format('hh:mm a').ms_to_text($ms)", "text[11:08 AM]")
        chk("rell.time.format('yyyyy.MMMMM.dd hh:mm aaa').ms_to_text($ms)", "text[02001.July.04 11:08 AM]")
        chk("rell.time.format('EEE, d MMM yyyy HH:mm:ss').ms_to_text($ms)", "text[Wed, 4 Jul 2001 11:08:56]")
        chk("rell.time.format('yyMMddHHmmss').ms_to_text($ms)", "text[010704110856]")
        chk("rell.time.format('yyyy-MM-dd\\'T\\'HH:mm:ss.SSS').ms_to_text($ms)", "text[2001-07-04T11:08:56.235]")
        chk("rell.time.format('yyyy-\\'W\\'ww-u').ms_to_text($ms)", "text[2001-W27-3]")
    }

    @Test fun testTextToMs() {
        val ex = "rt_err:fn:error:rell.time.format.text_to_ms:java.text.ParseException"
        chk("rell.time.format('yyyy.MM.dd \\'at\\' HH:mm:ss').text_to_ms('2001.07.04 at X1:08:56')", ex)
        chk("rell.time.format('EEE, MMM d, \\'\\'yy').text_to_ms('Ted, Jul 4, \\'01')", ex)
        chk("rell.time.format('h:mm a').text_to_ms('11:08 CM')", ex)
        chk("rell.time.format('hh \\'o\\'\\'clock\\' a').text_to_ms('-11 o\\'clock AM')", ex)
        chk("rell.time.format('hh:mm a').text_to_ms('11;08 AM')", ex)
        chk("rell.time.format('yyyyy.MMMMM.dd hh:mm aaa').text_to_ms('02001.July.04 11:08AM')", ex)
        chk("rell.time.format('EEE, d MMM yyyy HH:mm:ss').text_to_ms('Weds, 4 Jul 2001 11:08:56')", ex)
        chk("rell.time.format('yyMMddHHmmss').text_to_ms('01.07.04 11:08:56')", ex)
        chk("rell.time.format('yyyy-MM-dd\\'T\\'HH:mm:ss.SSS').text_to_ms('x2001-07-04T11:08:56.235')", ex)
        chk("rell.time.format('yyyy-\\'W\\'ww-u').text_to_ms('2001-S27-3')", ex)

        chk("rell.time.format('yyyy.MM.dd \\'at\\' HH:mm:ss').text_to_ms('2001.07.04 at 11:08:56')", "int[994244936000]")
        chk("rell.time.format('EEE, MMM d, \\'\\'yy').text_to_ms('Wed, Jul 4, \\'01')", "int[994204800000]")
        chk("rell.time.format('h:mm a').text_to_ms('11:08 AM')", "int[40080000]")
        chk("rell.time.format('hh \\'o\\'\\'clock\\' a').text_to_ms('11 o\\'clock AM')", "int[39600000]")
        chk("rell.time.format('hh:mm a').text_to_ms('11:08 AM')", "int[40080000]")
        chk("rell.time.format('yyyyy.MMMMM.dd hh:mm aaa').text_to_ms('02001.July.04 11:08 AM')", "int[994244880000]")
        chk("rell.time.format('EEE, d MMM yyyy HH:mm:ss').text_to_ms('Wed, 4 Jul 2001 11:08:56')", "int[994244936000]")
        chk("rell.time.format('yyMMddHHmmss').text_to_ms('010704110856')", "int[994244936000]")
        chk("rell.time.format('yyyy-MM-dd\\'T\\'HH:mm:ss.SSS').text_to_ms('2001-07-04T11:08:56.235')", "int[994244936235]")
        chk("rell.time.format('yyyy-\\'W\\'ww-u').text_to_ms('2001-W27-3')", "int[994204800000]")
    }

    @Test fun testTextToMsOrNull() {
        chk("rell.time.format('yyyy.MM.dd \\'at\\' HH:mm:ss').text_to_ms_or_null('2001.07.04 at X1:08:56')", "null")
        chk("rell.time.format('EEE, MMM d, \\'\\'yy').text_to_ms_or_null('Ted, Jul 4, \\'01')", "null")
        chk("rell.time.format('h:mm a').text_to_ms_or_null('11:08 CM')", "null")
        chk("rell.time.format('hh \\'o\\'\\'clock\\' a').text_to_ms_or_null('-11 o\\'clock AM')", "null")
        chk("rell.time.format('hh:mm a').text_to_ms_or_null('11;08 AM')", "null")
        chk("rell.time.format('yyyyy.MMMMM.dd hh:mm aaa').text_to_ms_or_null('02001.July.04 11:08AM')", "null")
        chk("rell.time.format('EEE, d MMM yyyy HH:mm:ss').text_to_ms_or_null('Weds, 4 Jul 2001 11:08:56')", "null")
        chk("rell.time.format('yyMMddHHmmss').text_to_ms_or_null('01.07.04 11:08:56')", "null")
        chk("rell.time.format('yyyy-MM-dd\\'T\\'HH:mm:ss.SSS').text_to_ms_or_null('x2001-07-04T11:08:56.235')", "null")
        chk("rell.time.format('yyyy-\\'W\\'ww-u').text_to_ms_or_null('2001-S27-3')", "null")

        chk("rell.time.format('yyyy.MM.dd \\'at\\' HH:mm:ss').text_to_ms_or_null('2001.07.04 at 11:08:56')", "int[994244936000]")
        chk("rell.time.format('EEE, MMM d, \\'\\'yy').text_to_ms_or_null('Wed, Jul 4, \\'01')", "int[994204800000]")
        chk("rell.time.format('h:mm a').text_to_ms_or_null('11:08 AM')", "int[40080000]")
        chk("rell.time.format('hh \\'o\\'\\'clock\\' a').text_to_ms_or_null('11 o\\'clock AM')", "int[39600000]")
        chk("rell.time.format('hh:mm a').text_to_ms_or_null('11:08 AM')", "int[40080000]")
        chk("rell.time.format('yyyyy.MMMMM.dd hh:mm aaa').text_to_ms_or_null('02001.July.04 11:08 AM')", "int[994244880000]")
        chk("rell.time.format('EEE, d MMM yyyy HH:mm:ss').text_to_ms_or_null('Wed, 4 Jul 2001 11:08:56')", "int[994244936000]")
        chk("rell.time.format('yyMMddHHmmss').text_to_ms_or_null('010704110856')", "int[994244936000]")
        chk("rell.time.format('yyyy-MM-dd\\'T\\'HH:mm:ss.SSS').text_to_ms_or_null('2001-07-04T11:08:56.235')", "int[994244936235]")
        chk("rell.time.format('yyyy-\\'W\\'ww-u').text_to_ms_or_null('2001-W27-3')", "int[994204800000]")
    }

    @Test fun testToText() {
        chk("rell.time.format('yyyy.MM.dd \\'at\\' HH:mm:ss').to_text()", "text[yyyy.MM.dd 'at' HH:mm:ss]")
        chk("rell.time.format('EEE, MMM d, \\'\\'yy').to_text()", "text[EEE, MMM d, ''yy]")
        chk("rell.time.format('h:mm a').to_text()", "text[h:mm a]")
        chk("rell.time.format('hh \\'o\\'\\'clock\\' a').to_text()", "text[hh 'o''clock' a]")
        chk("rell.time.format('hh:mm a').to_text()", "text[hh:mm a]")
        chk("rell.time.format('yyyyy.MMMMM.dd hh:mm aaa').to_text()", "text[yyyyy.MMMMM.dd hh:mm aaa]")
        chk("rell.time.format('EEE, d MMM yyyy HH:mm:ss').to_text()", "text[EEE, d MMM yyyy HH:mm:ss]")
        chk("rell.time.format('yyMMddHHmmss').to_text()", "text[yyMMddHHmmss]")
        chk("rell.time.format('yyyy-MM-dd\\'T\\'HH:mm:ss.SSS').to_text()", "text[yyyy-MM-dd'T'HH:mm:ss.SSS]")
        chk("rell.time.format('yyyy-\\'W\\'ww-u').to_text()", "text[yyyy-'W'ww-u]")
    }

    @Test fun testStaticMsToText() {
        val ms = "994244936235"
        chk("rell.time.ms_to_text('yyyy.MM.dd \\'at\\' HH:mm:ss', $ms)", "text[2001.07.04 at 11:08:56]")
        chk("rell.time.ms_to_text('EEE, MMM d, \\'\\'yy', $ms)", "text[Wed, Jul 4, '01]")
        chk("rell.time.ms_to_text('h:mm a', $ms)", "text[11:08 AM]")
        chk("rell.time.ms_to_text('hh \\'o\\'\\'clock\\' a', $ms)", "text[11 o'clock AM]")
        chk("rell.time.ms_to_text('hh:mm a', $ms)", "text[11:08 AM]")
        chk("rell.time.ms_to_text('yyyyy.MMMMM.dd hh:mm aaa', $ms)", "text[02001.July.04 11:08 AM]")
        chk("rell.time.ms_to_text('EEE, d MMM yyyy HH:mm:ss', $ms)", "text[Wed, 4 Jul 2001 11:08:56]")
        chk("rell.time.ms_to_text('yyMMddHHmmss', $ms)", "text[010704110856]")
        chk("rell.time.ms_to_text('yyyy-MM-dd\\'T\\'HH:mm:ss.SSS', $ms)", "text[2001-07-04T11:08:56.235]")
        chk("rell.time.ms_to_text('yyyy-\\'W\\'ww-u', $ms)", "text[2001-W27-3]")
    }

    @Test fun testStaticTextToMs() {
        val ex = "rt_err:fn:error:rell.time.text_to_ms:java.text.ParseException"
        chk("rell.time.text_to_ms('yyyy.MM.dd \\'at\\' HH:mm:ss', '2001.07.04 at X1:08:56')", ex)
        chk("rell.time.text_to_ms('EEE, MMM d, \\'\\'yy', 'Ted, Jul 4, \\'01')", ex)
        chk("rell.time.text_to_ms('h:mm a', '11:08 CM')", ex)
        chk("rell.time.text_to_ms('hh \\'o\\'\\'clock\\' a', '-11 o\\'clock AM')", ex)
        chk("rell.time.text_to_ms('hh:mm a', '11;08 AM')", ex)
        chk("rell.time.text_to_ms('yyyyy.MMMMM.dd hh:mm aaa', '02001.July.04 11:08AM')", ex)
        chk("rell.time.text_to_ms('EEE, d MMM yyyy HH:mm:ss', 'Weds, 4 Jul 2001 11:08:56')", ex)
        chk("rell.time.text_to_ms('yyMMddHHmmss', '01.07.04 11:08:56')", ex)
        chk("rell.time.text_to_ms('yyyy-MM-dd\\'T\\'HH:mm:ss.SSS', 'x2001-07-04T11:08:56.235')", ex)
        chk("rell.time.text_to_ms('yyyy-\\'W\\'ww-u', '2001-S27-3')", ex)

        chk("rell.time.text_to_ms('yyyy.MM.dd \\'at\\' HH:mm:ss', '2001.07.04 at 11:08:56')", "int[994244936000]")
        chk("rell.time.text_to_ms('EEE, MMM d, \\'\\'yy', 'Wed, Jul 4, \\'01')", "int[994204800000]")
        chk("rell.time.text_to_ms('h:mm a', '11:08 AM')", "int[40080000]")
        chk("rell.time.text_to_ms('hh \\'o\\'\\'clock\\' a', '11 o\\'clock AM')", "int[39600000]")
        chk("rell.time.text_to_ms('hh:mm a', '11:08 AM')", "int[40080000]")
        chk("rell.time.text_to_ms('yyyyy.MMMMM.dd hh:mm aaa', '02001.July.04 11:08 AM')", "int[994244880000]")
        chk("rell.time.text_to_ms('EEE, d MMM yyyy HH:mm:ss', 'Wed, 4 Jul 2001 11:08:56')", "int[994244936000]")
        chk("rell.time.text_to_ms('yyMMddHHmmss', '010704110856')", "int[994244936000]")
        chk("rell.time.text_to_ms('yyyy-MM-dd\\'T\\'HH:mm:ss.SSS', '2001-07-04T11:08:56.235')", "int[994244936235]")
        chk("rell.time.text_to_ms('yyyy-\\'W\\'ww-u', '2001-W27-3')", "int[994204800000]")
    }

    @Test fun testStaticTextToMsOrNull() {
        chk("rell.time.text_to_ms_or_null('yyyy.MM.dd \\'at\\' HH:mm:ss', '2001.07.04 at X1:08:56')", "null")
        chk("rell.time.text_to_ms_or_null('EEE, MMM d, \\'\\'yy', 'Ted, Jul 4, \\'01')", "null")
        chk("rell.time.text_to_ms_or_null('h:mm a', '11:08 CM')", "null")
        chk("rell.time.text_to_ms_or_null('hh \\'o\\'\\'clock\\' a', '-11 o\\'clock AM')", "null")
        chk("rell.time.text_to_ms_or_null('hh:mm a', '11;08 AM')", "null")
        chk("rell.time.text_to_ms_or_null('yyyyy.MMMMM.dd hh:mm aaa', '02001.July.04 11:08AM')", "null")
        chk("rell.time.text_to_ms_or_null('EEE, d MMM yyyy HH:mm:ss', 'Weds, 4 Jul 2001 11:08:56')", "null")
        chk("rell.time.text_to_ms_or_null('yyMMddHHmmss', '01.07.04 11:08:56')", "null")
        chk("rell.time.text_to_ms_or_null('yyyy-MM-dd\\'T\\'HH:mm:ss.SSS', 'x2001-07-04T11:08:56.235')", "null")
        chk("rell.time.text_to_ms_or_null('yyyy-\\'W\\'ww-u', '2001-S27-3')", "null")

        chk("rell.time.text_to_ms_or_null('yyyy.MM.dd \\'at\\' HH:mm:ss', '2001.07.04 at 11:08:56')", "int[994244936000]")
        chk("rell.time.text_to_ms_or_null('EEE, MMM d, \\'\\'yy', 'Wed, Jul 4, \\'01')", "int[994204800000]")
        chk("rell.time.text_to_ms_or_null('h:mm a', '11:08 AM')", "int[40080000]")
        chk("rell.time.text_to_ms_or_null('hh \\'o\\'\\'clock\\' a', '11 o\\'clock AM')", "int[39600000]")
        chk("rell.time.text_to_ms_or_null('hh:mm a', '11:08 AM')", "int[40080000]")
        chk("rell.time.text_to_ms_or_null('yyyyy.MMMMM.dd hh:mm aaa', '02001.July.04 11:08 AM')", "int[994244880000]")
        chk("rell.time.text_to_ms_or_null('EEE, d MMM yyyy HH:mm:ss', 'Wed, 4 Jul 2001 11:08:56')", "int[994244936000]")
        chk("rell.time.text_to_ms_or_null('yyMMddHHmmss', '010704110856')", "int[994244936000]")
        chk("rell.time.text_to_ms_or_null('yyyy-MM-dd\\'T\\'HH:mm:ss.SSS', '2001-07-04T11:08:56.235')", "int[994244936235]")
        chk("rell.time.text_to_ms_or_null('yyyy-\\'W\\'ww-u', '2001-W27-3')", "int[994204800000]")
    }
}