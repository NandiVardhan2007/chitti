package com.owlcoders.chitti

import com.owlcoders.chitti.documents.DocumentKind
import com.owlcoders.chitti.documents.IdParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdParserTest {

    /** A 12-digit number with a correct Verhoeff check digit, built from its first 11 digits. */
    private fun validAadhaar(first11: String): String = first11 + IdParser.aadhaarCheckDigit(first11)

    private val aadhaar = validAadhaar("23456789012")
    private val aadhaarSpaced = IdParser.formatAadhaar(aadhaar)

    @Test fun verhoeffAcceptsValidAndRejectsOneDigitChange() {
        assertTrue(IdParser.isValidAadhaar(aadhaar))
        val last = aadhaar.last().digitToInt()
        val wrong = aadhaar.dropLast(1) + ((last + 1) % 10)
        assertFalse(IdParser.isValidAadhaar(wrong))
        // Aadhaar numbers never start with 0 or 1.
        assertFalse(IdParser.isValidAadhaar("1" + aadhaar.drop(1)))
    }

    @Test fun aadhaarFront() {
        val text = """
            भारत सरकार
            Government of India
            Ravi Kumar Reddy
            जन्म तिथि/DOB: 12/05/1998
            पुरुष / MALE
            $aadhaarSpaced
            मेरा आधार, मेरी पहचान
        """.trimIndent()
        val f = IdParser.parse(text)
        assertEquals(DocumentKind.AADHAAR, f.kind)
        assertEquals("Ravi Kumar Reddy", f.name)
        assertEquals("12/05/1998", f.dateOfBirth)
        assertEquals("Male", f.gender)
        assertEquals(aadhaarSpaced, f.aadhaarNumber)
    }

    @Test fun aadhaarBackAddressAndFather() {
        val text = """
            Unique Identification Authority of India
            Address: S/O Rama Rao, 1-2-34, Gandhi Nagar,
            Kakinada, East Godavari, Andhra Pradesh - 533003
            $aadhaarSpaced
        """.trimIndent()
        val f = IdParser.parse(text)
        assertEquals(DocumentKind.AADHAAR, f.kind)
        assertEquals("Rama Rao", f.fatherName)
        assertTrue(f.address, f.address.startsWith("1-2-34, Gandhi Nagar"))
        assertTrue(f.address, f.address.endsWith("533003"))
    }

    @Test fun virtualIdIsNotTakenForAadhaar() {
        // A 16-digit VID must not be read as an Aadhaar number, even if its first 12 digits pass.
        val vid = "$aadhaarSpaced 1234"
        assertEquals("", IdParser.findAadhaar("VID : $vid"))
    }

    @Test fun invalidChecksumIsNotSaved() {
        val wrong = aadhaar.dropLast(1) + ((aadhaar.last().digitToInt() + 3) % 10)
        assertEquals("", IdParser.findAadhaar("Aadhaar ${IdParser.formatAadhaar(wrong)}"))
    }

    @Test fun panNewLayout() {
        val text = """
            आयकर विभाग INCOME TAX DEPARTMENT
            भारत सरकार GOVT. OF INDIA
            स्थायी लेखा संख्या कार्ड
            Permanent Account Number Card
            ABCPR1234K
            नाम / Name
            RAVI KUMAR REDDY
            पिता का नाम / Father's Name
            RAMA RAO
            जन्म की तारीख / Date of Birth
            12/05/1998
        """.trimIndent()
        val f = IdParser.parse(text)
        assertEquals(DocumentKind.PAN, f.kind)
        assertEquals("ABCPR1234K", f.panNumber)
        assertEquals("Ravi Kumar Reddy", f.name)
        assertEquals("Rama Rao", f.fatherName)
        assertEquals("12/05/1998", f.dateOfBirth)
    }

    @Test fun panOldLayout() {
        val text = """
            INCOME TAX DEPARTMENT GOVT. OF INDIA
            RAVI KUMAR REDDY
            RAMA RAO
            12-05-1998
            Permanent Account Number
            ABCPR1234K
        """.trimIndent()
        val f = IdParser.parse(text)
        assertEquals("ABCPR1234K", f.panNumber)
        assertEquals("Ravi Kumar Reddy", f.name)
        assertEquals("Rama Rao", f.fatherName)
        assertEquals("12/05/1998", f.dateOfBirth)
    }

    @Test fun rationCard() {
        val text = """
            Government of Telangana
            Civil Supplies Department
            Food Security Card
            FSC Ref No: WAP1234567890
            Head of the Family: LAKSHMI DEVI
            Address: 4-5-6, Ameerpet, Hyderabad - 500016
        """.trimIndent()
        val f = IdParser.parse(text)
        assertEquals(DocumentKind.RATION, f.kind)
        assertEquals("WAP1234567890", f.rationCardNumber)
        assertEquals("Lakshmi Devi", f.name)
        assertTrue(f.address, f.address.endsWith("500016"))
    }

    @Test fun birthCertificate() {
        val text = """
            GOVERNMENT OF ANDHRA PRADESH
            BIRTH CERTIFICATE
            Name: Ravi Kumar Reddy
            Sex: Male
            Date of Birth: 12.05.1998
            Name of Father: Rama Rao
            Name of Mother: Sita Devi
        """.trimIndent()
        val f = IdParser.parse(text)
        assertEquals(DocumentKind.BIRTH, f.kind)
        assertEquals("Ravi Kumar Reddy", f.name)
        assertEquals("12/05/1998", f.dateOfBirth)
        assertEquals("Male", f.gender)
        assertEquals("Rama Rao", f.fatherName)
    }

    @Test fun masking() {
        assertEquals("XXXX XXXX ${aadhaar.takeLast(4)}", IdParser.maskAadhaar(aadhaarSpaced))
        assertEquals("ABXXXXX34K", IdParser.maskPan("ABCPR1234K"))
    }

    @Test fun unrelatedTextIsOther() {
        val f = IdParser.parse("Hackathon schedule\nDay 1 starts at 9 am")
        assertEquals(DocumentKind.OTHER, f.kind)
        assertTrue(f.isEmpty)
    }
}
