package com.stupidtree.hitax.utils

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

object HTMLUtils {
    fun getStringValueByClass(d: Element, className: String?): String {
        val name = className ?: return ""
        return try {
            d.getElementsByClass(name).first()?.text().orEmpty()
        } catch (e: Exception) {
            ""
        }
    }

    fun getAttrValueByClass(d: Element, className: String?, attr: String?): String {
        val name = className ?: return ""
        val attrName = attr ?: return ""
        return try {
            d.getElementsByClass(name).first()?.attr(attrName).orEmpty()
        } catch (e: Exception) {
            ""
        }
    }

    fun getAttrValueInTag(d: Element, attr: String?, tag: String?): String {
        val attrName = attr ?: return ""
        val tagName = tag ?: return ""
        return try {
            d.getElementsByTag(tagName).first()?.attr(attrName).orEmpty()
        } catch (e: Exception) {
            ""
        }
    }

    fun getTextOfTag(d: Element, tag: String?): String {
        val tagName = tag ?: return ""
        return try {
            d.getElementsByTag(tagName).first()?.text().orEmpty()
        } catch (e: Exception) {
            ""
        }
    }

    fun getTextOfTagHavingAttr(d: Element, tag: String?, attr: String?): String {
        return try {
            for (e in d.getElementsByTag(tag)) {
                if (e.hasAttr(attr)) return e.text()
            }
            ""
        } catch (e: Exception) {
            ""
        }
    }

    fun getElementsInClassByTag(d: Document, className: String?, tag: String?): Elements {
        val name = className ?: return Elements()
        val tagName = tag ?: return Elements()
        return try {
            d.getElementsByClass(name).first()?.getElementsByTag(tagName) ?: Elements()
        } catch (e: Exception) {
            e.printStackTrace()
            Elements()
        }
    }
}
