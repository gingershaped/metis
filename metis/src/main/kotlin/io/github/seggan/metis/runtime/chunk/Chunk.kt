package io.github.seggan.metis.runtime.chunk

import io.github.seggan.metis.MetisRuntimeException
import io.github.seggan.metis.parsing.Span
import io.github.seggan.metis.runtime.State
import io.github.seggan.metis.runtime.getFromTop
import io.github.seggan.metis.runtime.pop
import io.github.seggan.metis.runtime.push
import io.github.seggan.metis.runtime.values.Arity
import io.github.seggan.metis.runtime.values.CallableValue
import io.github.seggan.metis.runtime.values.Value
import io.github.seggan.metis.runtime.values.initChunk

class Chunk(
    val name: String,
    val insns: List<Insn>,
    override val arity: Arity,
    spans: List<Span>,
    val file: Pair<String, String>
) : CallableValue {

    val spans = spans.map { it.copy(file = file) }

    override var metatable: Value.Table? = Companion.metatable

    companion object {
        private val metatable = initChunk()
    }

    override fun call(nargs: Int): CallableValue.Executor = ChunkExecutor()

    override fun toString(): String {
        return buildString {
            append("=== ")
            append(name)
            appendLine(" ===")
            for ((i, insn) in insns.withIndex()) {
                append(i)
                append(": ")
                append(insn)
                append(" (")
                val span = spans[i]
                append(span.start)
                append("..")
                append(span.end)
                appendLine(")")
            }
        }
    }

    private inner class ChunkExecutor : CallableValue.Executor {
        private var ip = 0

        override fun step(state: State): StepResult {
            if (ip >= insns.size) return StepResult.FINISHED
            try {
                val insn = insns[ip++]
                when (insn) {
                    is Insn.BinaryOp -> TODO()
                    is Insn.GetGlobals -> state.stack.push(state.globals)
                    is Insn.GetLocal -> state.stack.push(state.stack[state.localsOffset + insn.index])
                    is Insn.Index -> state.index()
                    is Insn.IndexImm -> state.indexImm(insn.key)
                    is Insn.ListIndexImm -> state.listIndexImm(insn.key)
                    is Insn.Pop -> state.stack.pop()
                    is Insn.Push -> state.stack.push(insn.value)
                    is Insn.CopyUnder -> state.stack.push(state.stack.getFromTop(insn.index))
                    is Insn.Set -> state.set()
                    is Insn.SetImm -> state.setImm(insn.key, insn.allowNew)
                    is Insn.UnaryOp -> TODO()
                    is Insn.Call -> state.call(insn.nargs, spans[ip - 1])
                    is Insn.Return -> {
                        val value = state.stack.pop()
                        state.unwindStack()
                        state.stack.push(value)
                        return StepResult.FINISHED
                    }
                }
                if (state.debugMode) {
                    println(insn)
                }
            } catch (e: MetisRuntimeException) {
                e.addStackFrame(spans[ip - 1])
                throw e
            }
            return StepResult.CONTINUE
        }
    }
}