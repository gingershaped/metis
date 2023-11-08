package io.github.seggan.metis.runtime.intrinsics

import io.github.seggan.metis.runtime.*
import io.github.seggan.metis.util.push
import java.nio.file.Path
import java.util.regex.PatternSyntaxException
import kotlin.collections.set
import kotlin.io.path.*
import kotlin.math.*

/**
 * A native library that can be loaded into a [State] and imported with `require`
 *
 * @param name The name of the library
 */
abstract class NativeLibrary(val name: String) : OneShotFunction(Arity.ZERO) {
    final override fun execute(state: State, nargs: Int) {
        state.globals.putAll(buildTable(::init))
        state.stack.push(Value.Null)
    }

    abstract fun init(lib: MutableMap<String, Value>)
}

/**
 * The `regex` library
 */
object RegexLib : NativeLibrary("regex") {
    override fun init(lib: MutableMap<String, Value>) {
        lib["compile"] = oneArgFunction { s ->
            val pattern = s.stringValue()
            try {
                Value.Native(Regex(pattern))
            } catch (e: PatternSyntaxException) {
                throw MetisRuntimeException(
                    "RegexError",
                    "Failed to compile regex pattern: $pattern",
                    buildTable { it["pattern"] = pattern.metisValue() }
                )
            }
        }
        lib["escape"] = oneArgFunction { s ->
            val regex = s.stringValue()
            Value.String(Regex.escape(regex))
        }

        lib["match"] = twoArgFunction(true) { self, other ->
            val regex = self.asObj<Regex>()
            val input = other.stringValue()
            val match = regex.find(input)
            if (match != null) {
                val groups = Value.Table()
                match.groups.forEachIndexed { index, group ->
                    groups[index.metisValue()] = group?.value?.metisValue() ?: Value.Null
                }
                groups
            } else {
                Value.Null
            }
        }
        lib["replace"] = threeArgFunction(true) { self, other, replacement ->
            val regex = self.asObj<Regex>()
            val input = other.stringValue()
            val repl = replacement.stringValue()
            regex.replace(input, repl).metisValue()
        }
        lib["split"] = twoArgFunction(true) { self, other ->
            val regex = self.asObj<Regex>()
            val input = other.stringValue()
            val list = Value.List()
            regex.split(input).forEach {
                list.add(it.metisValue())
            }
            list
        }
    }
}

/**
 * The `os` library
 */
object OsLib : NativeLibrary("os") {
    override fun init(lib: MutableMap<String, Value>) {
        lib["get_env"] = oneArgFunction { value ->
            System.getenv(value.stringValue())?.metisValue() ?: Value.Null
        }
        lib["set_env"] = twoArgFunction { value, other ->
            System.setProperty(value.stringValue(), other.stringValue())
            Value.Null
        }
        lib["get_cwd"] = zeroArgFunction { currentDir.absolutePathString().metisValue() }
        lib["set_cwd"] = oneArgFunction { p ->
            val path = fileSystem.getPath(p.stringValue())
            if (!path.isAbsolute) {
                throw MetisRuntimeException(
                    "IoError",
                    "Cannot set cwd to relative path: ${path.absolutePathString()}"
                )
            }
            currentDir = path
            Value.Null
        }
    }
}

/**
 * The `path` library's native functions
 */
object PathLib : NativeLibrary("__path") {

    private inline fun pathFunction(crossinline fn: (Path) -> Value) = oneArgFunction { self ->
        translateIoError { fn(toPath(self)) }
    }

    private fun State.toPath(value: Value) = currentDir.resolve(fileSystem.getPath(value.stringValue()))

    @OptIn(ExperimentalPathApi::class)
    override fun init(lib: MutableMap<String, Value>) {
        lib["separator"] = Value.String(System.getProperty("file.separator"))
        lib["normalize"] = pathFunction { it.normalize().toString().metisValue() }
        lib["absolute"] = pathFunction { it.toAbsolutePath().toString().metisValue() }
        lib["resolve"] = twoArgFunction { self, other ->
            translateIoError {
                toPath(self)
                    .resolve(other.stringValue())
                    .toString()
                    .metisValue()
            }
        }
        lib["parent"] = pathFunction { it.parent.toString().metisValue() }
        lib["file_name"] = pathFunction { it.fileName.toString().metisValue() }
        lib["root"] = pathFunction { it.root.toString().metisValue() }
        lib["is_absolute"] = pathFunction { it.isAbsolute.metisValue() }
        lib["list"] = pathFunction { path ->
            val list = Value.List()
            path.toFile().listFiles()?.forEach {
                list.add(it.absolutePath.metisValue())
            }
            list
        }
        lib["exists"] = pathFunction { it.exists().metisValue() }
        lib["is_file"] = pathFunction { it.isRegularFile().metisValue() }
        lib["is_dir"] = pathFunction { it.isDirectory().metisValue() }
        lib["is_symlink"] = pathFunction { it.isSymbolicLink().metisValue() }
        lib["is_hidden"] = pathFunction { it.isHidden().metisValue() }
        lib["move"] = twoArgFunction { src, dest ->
            translateIoError {
                toPath(src).moveTo(toPath(dest))
            }
            Value.Null
        }
        lib["delete"] = pathFunction { it.deleteIfExists().metisValue() }
        lib["create_dir"] = pathFunction { it.createDirectory().absolutePathString().metisValue() }
        lib["create_dirs"] = pathFunction { it.createDirectories().absolutePathString().metisValue() }
        lib["delete_recursive"] = pathFunction { it.deleteRecursively(); Value.Null }
        lib["open_write"] = pathFunction { wrapOutStream(it.outputStream()) }
        lib["open_read"] = pathFunction { wrapInStream(it.inputStream()) }
    }
}

/**
 * The `math` library
 */
object MathLib : NativeLibrary("math") {

    private inline fun numberFunction(crossinline fn: (Double) -> Double): OneShotFunction {
        return oneArgFunction { self -> fn(self.doubleValue()).metisValue() }
    }

    private inline fun numberFunction(crossinline fn: (Double, Double) -> Double): OneShotFunction {
        return twoArgFunction { self, other -> fn(self.doubleValue(), other.doubleValue()).metisValue() }
    }

    override fun init(lib: MutableMap<String, Value>) {
        lib["pi"] = Math.PI.metisValue()
        lib["e"] = Math.E.metisValue()

        lib["abs"] = numberFunction(::abs)
        lib["ceil"] = numberFunction(::ceil)
        lib["floor"] = numberFunction(::floor)
        lib["round"] = numberFunction(::round)
        lib["sqrt"] = numberFunction(::sqrt)
        lib["cbrt"] = numberFunction(::cbrt)
        lib["exp"] = numberFunction(::exp)
        lib["expm1"] = numberFunction(::expm1)
        lib["ln"] = numberFunction(::ln)
        lib["log10"] = numberFunction(::log10)
        lib["log2"] = numberFunction(::log2)
        lib["pow"] = numberFunction(Double::pow)
        lib["hypot"] = numberFunction(::hypot)
        lib["sin"] = numberFunction(::sin)
        lib["cos"] = numberFunction(::cos)
        lib["tan"] = numberFunction(::tan)
        lib["asin"] = numberFunction(::asin)
        lib["acos"] = numberFunction(::acos)
        lib["atan"] = numberFunction(::atan)
        lib["atan2"] = numberFunction(::atan2)
        lib["sinh"] = numberFunction(::sinh)
        lib["cosh"] = numberFunction(::cosh)
        lib["tanh"] = numberFunction(::tanh)
        lib["asinh"] = numberFunction(::asinh)
        lib["acosh"] = numberFunction(::acosh)
        lib["atanh"] = numberFunction(::atanh)
        lib["sign"] = numberFunction(::sign)
    }
}