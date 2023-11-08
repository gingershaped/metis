package io.github.seggan.metis.runtime.intrinsics

import io.github.seggan.metis.runtime.*
import io.github.seggan.metis.runtime.chunk.Chunk
import java.nio.charset.Charset
import kotlin.math.pow

internal fun initString() = buildTable { table ->
    table["__str__"] = oneArgFunction(true) { it }
    table["__plus__"] = twoArgFunction(true) { self, other ->
        Value.String(self.stringValue() + other.stringValue())
    }
    table["__len__"] = oneArgFunction(true) { self ->
        self.stringValue().length.toDouble().metisValue()
    }
    table["__index__"] = twoArgFunction(true) { self, key ->
        self.lookUp(key) ?: throw MetisRuntimeException(
            "IndexError",
            "Index not found: ${stringify(key)}",
            buildTable { table ->
                table["index"] = key
                table["value"] = self
            }
        )
    }
    table["__contains__"] = twoArgFunction(true) { self, key ->
        self.stringValue().contains(key.stringValue()).metisValue()
    }
    table["__eq__"] = twoArgFunction(true) { self, other ->
        if (other !is Value.String) {
            Value.Boolean.FALSE
        } else {
            Value.Boolean.of(self.stringValue() == other.stringValue())
        }
    }
    table["__cmp__"] = twoArgFunction(true) { self, other ->
        self.stringValue().compareTo(other.stringValue()).metisValue()
    }
    table["encode"] = twoArgFunction(true) { self, encoding ->
        val actualEncoding =
            if (encoding == Value.Null) Charsets.UTF_8
            else Charset.forName(encoding.stringValue())
        Value.Bytes(self.stringValue().toByteArray(actualEncoding))
    }
    table["remove"] = threeArgFunction { self, start, end ->
        if (end == Value.Null) {
            self.stringValue().removeRange(start.intValue(), self.stringValue().length).metisValue()
        } else {
            self.stringValue().removeRange(start.intValue(), end.intValue()).metisValue()
        }
    }
    table["replace"] = threeArgFunction(true) { self, value, toReplace ->
        self.stringValue().replace(value.stringValue(), toReplace.stringValue()).metisValue()
    }
    table["split"] = twoArgFunction(true) { self, delimiter ->
        self.stringValue().split(delimiter.stringValue()).map(String::metisValue).metisValue()
    }
    table["sub"] = threeArgFunction(true) { self, start, end ->
        if (end == Value.Null) {
            self.stringValue().substring(start.intValue()).metisValue()
        } else {
            self.stringValue().substring(start.intValue(), end.intValue()).metisValue()
        }
    }
    table["equal_ignore_case"] = twoArgFunction(true) { self, other ->
        self.stringValue().equals(other.stringValue(), ignoreCase = true).metisValue()
    }

    table["builder"] = oneArgFunction { init ->
        when (init) {
            Value.Null -> wrapStringBuilder(StringBuilder())
            is Value.String -> wrapStringBuilder(StringBuilder(init.stringValue()))
            else -> wrapStringBuilder(StringBuilder(init.intValue()))
        }
    }
}

internal fun initNumber() = buildTable { table ->
    table["__str__"] = oneArgFunction(true) { self ->
        self.toString().metisValue()
    }
    table["__plus__"] = twoArgFunction(true) { self, other ->
        Value.Number.of(self.doubleValue() + other.doubleValue())
    }
    table["__minus__"] = twoArgFunction(true) { self, other ->
        Value.Number.of(self.doubleValue() - other.doubleValue())
    }
    table["__times__"] = twoArgFunction(true) { self, other ->
        Value.Number.of(self.doubleValue() * other.doubleValue())
    }
    table["__div__"] = twoArgFunction(true) { self, other ->
        Value.Number.of(self.doubleValue() / other.doubleValue())
    }
    table["__mod__"] = twoArgFunction(true) { self, other ->
        Value.Number.of(self.doubleValue() % other.doubleValue())
    }
    table["__pow__"] = twoArgFunction(true) { self, other ->
        self.doubleValue().pow(other.doubleValue()).metisValue()
    }
    table["__eq__"] = twoArgFunction(true) { self, other ->
        if (other !is Value.Number) {
            Value.Boolean.FALSE
        } else {
            Value.Boolean.of(self.doubleValue() == other.doubleValue())
        }
    }
    table["__cmp__"] = twoArgFunction(true) { self, other ->
        self.doubleValue().compareTo(other.doubleValue()).metisValue()
    }
    table["__neg__"] = oneArgFunction(true) { self ->
        (-self.doubleValue()).metisValue()
    }
    table["string_with_radix"] = twoArgFunction(true) { self, radix ->
        self.intValue().toString(radix.intValue()).metisValue()
    }

    table["parse"] = twoArgFunction { s, radix ->
        try {
            if (radix == Value.Null) {
                s.stringValue().toDouble().metisValue()
            } else {
                s.stringValue().toInt(radix.intValue()).metisValue()
            }
        } catch (e: NumberFormatException) {
            throw MetisRuntimeException("ValueError", "Invalid number: ${s.stringValue()}")
        }
    }
    table["nan"] = Value.Number.NAN
    table["inf"] = Value.Number.INF
    table["neg_inf"] = Value.Number.NEG_INF
}

internal fun initBoolean() = buildTable { table ->
    table["__str__"] = oneArgFunction(true) { self ->
        self.toString().metisValue()
    }
    table["__eq__"] = twoArgFunction(true) { self, other ->
        if (other !is Value.Boolean) {
            Value.Boolean.FALSE
        } else {
            Value.Boolean.of(self.convertTo<Value.Boolean>().value == other.convertTo<Value.Boolean>().value)
        }
    }

    table["parse"] = oneArgFunction { s ->
        s.stringValue().toBoolean().metisValue()
    }
}

internal fun initTable() = Value.Table(mutableMapOf(), null).also { table ->
    table["__index__"] = twoArgFunction(true) { self, key ->
        self.lookUp(key) ?: throw MetisRuntimeException(
            "KeyError",
            "Key '${stringify(key)}' not found on ${stringify(self)}",
            buildTable { table ->
                table["key"] = key
                table["value"] = self
            }
        )
    }
    table["__set__"] = threeArgFunction(true) { self, key, value ->
        self.setOrError(key, value)
        Value.Null
    }
    table["__len__"] = oneArgFunction(true) { self ->
        self.convertTo<Value.Table>().size.metisValue()
    }
    table["__contains__"] = twoArgFunction(true) { self, key ->
        Value.Boolean.of(key in self.convertTo<Value.Table>())
    }
    table["keys"] = oneArgFunction(true) { self ->
        self.convertTo<Value.Table>().keys.metisValue()
    }
    table["values"] = oneArgFunction(true) { self ->
        self.convertTo<Value.Table>().values.metisValue()
    }
    table["remove"] = twoArgFunction(true) { self, key ->
        self.convertTo<Value.Table>().remove(key).orNull()
    }

    table.metatable = table
}

internal fun initList() = buildTable { table ->
    table["__str__"] = oneArgFunction(true) { self ->
        self.convertTo<Value.List>().toString().metisValue()
    }
    table["__index__"] = twoArgFunction(true) { self, key ->
        self.lookUp(key) ?: throw MetisRuntimeException(
            "IndexError",
            "Index not found: ${stringify(key)}",
            buildTable { table ->
                table["index"] = key
                table["value"] = self
            }
        )
    }
    table["__set__"] = threeArgFunction(true) { self, key, value ->
        self.setOrError(key, value)
        Value.Null
    }
    table["__len__"] = oneArgFunction(true) { self ->
        self.convertTo<Value.List>().size.metisValue()
    }
    table["__contains__"] = twoArgFunction(true) { self, key ->
        Value.Boolean.of(key in self.convertTo<Value.List>())
    }
    table["append"] = twoArgFunction(true) { self, value ->
        self.convertTo<Value.List>().add(value)
        Value.Null
    }
    table["remove"] = twoArgFunction(true) { self, value ->
        self.convertTo<Value.List>().remove(value)
        Value.Null
    }
    table["remove_at"] = twoArgFunction(true) { self, index ->
        self.convertTo<Value.List>().removeAt(index.intValue())
    }
    table["slice"] = threeArgFunction(true) { self, start, end ->
        if (end == Value.Null) {
            self.convertTo<Value.List>().subList(start.intValue(), self.convertTo<Value.List>().size).metisValue()
        } else {
            self.convertTo<Value.List>().subList(start.intValue(), end.intValue()).metisValue()
        }
    }

    table["new"] = oneArgFunction { size ->
        if (size == Value.Null) {
            Value.List()
        } else {
            Value.List(ArrayList(size.intValue()))
        }
    }
}

internal fun initBytes() = buildTable { table ->
    table["__str__"] = oneArgFunction(true) { self ->
        self.convertTo<Value.Bytes>().value.toString(Charsets.UTF_8).metisValue()
    }
    table["__index__"] = twoArgFunction(true) { self, key ->
        self.lookUp(key) ?: throw MetisRuntimeException(
            "IndexError",
            "Byte not found: ${stringify(key)}",
            buildTable { table ->
                table["index"] = key
                table["value"] = self
            }
        )
    }
    table["__set__"] = threeArgFunction(true) { self, key, value ->
        self.setOrError(key, value)
        Value.Null
    }
    table["__len__"] = oneArgFunction(true) { self ->
        self.convertTo<Value.Bytes>().value.size.metisValue()
    }
    table["__contains__"] = twoArgFunction(true) { self, key ->
        Value.Boolean.of(key.intValue().toByte() in self.convertTo<Value.Bytes>().value)
    }
    table["decode"] = twoArgFunction(true) { self, encoding ->
        val actualEncoding =
            if (encoding == Value.Null) Charsets.UTF_8
            else Charset.forName(encoding.stringValue())
        self.convertTo<Value.Bytes>().value.toString(actualEncoding).metisValue()
    }
    table["slice"] = threeArgFunction(true) { self, start, len ->
        val newBytes = ByteArray(len.intValue())
        self.convertTo<Value.Bytes>().value.copyInto(
            newBytes,
            0,
            start.intValue(),
            start.intValue() + len.intValue()
        )
        newBytes.metisValue()
    }

    table["allocate"] = oneArgFunction { size ->
        Value.Bytes(ByteArray(size.intValue()))
    }
    table["concat"] = oneArgFunction { list ->
        val bytes = list.convertTo<Value.List>().map { it.convertTo<Value.Bytes>().value }
        val size = bytes.sumOf { it.size }
        val newBytes = ByteArray(size)
        var offset = 0
        bytes.forEach {
            it.copyInto(newBytes, offset)
            offset += it.size
        }
        newBytes.metisValue()
    }
}

internal fun initNull() = buildTable { table ->
    table["__str__"] = oneArgFunction(true) {
        "null".metisValue()
    }
    table["__call__"] = oneArgFunction(true) {
        throw MetisRuntimeException("TypeError", "Cannot call null")
    }
    table["__eq__"] = twoArgFunction(true) { _, other ->
        Value.Boolean.of(other == Value.Null)
    }
    table["__set__"] = threeArgFunction(true) { _, _, _ ->
        throw MetisRuntimeException("TypeError", "Cannot set any key on null")
    }
}

internal fun initChunk() = buildTable { table ->
    table["__str__"] = oneArgFunction(true) { self ->
        self.convertTo<Chunk.Instance>().toString().metisValue()
    }
    table["disassemble"] = oneArgFunction(true) { self ->
        self.convertTo<Chunk.Instance>().dissasemble().metisValue()
    }
}

internal fun initError() = buildTable { table ->
    table["__str__"] = oneArgFunction(true) { self ->
        self.convertTo<MetisRuntimeException>().message!!.metisValue()
    }
    table["__call__"] = oneArgFunction(true) {
        throw MetisRuntimeException("TypeError", "Cannot call error")
    }
    table["__eq__"] = twoArgFunction(true) { self, other ->
        Value.Boolean.of(self === other)
    }
    table["__contains__"] = twoArgFunction(true) { self, key ->
        Value.Boolean.of(self.lookUp(key) != null)
    }
}