package io.github.seggan.metis.compilation

import io.github.seggan.metis.runtime.Value
import io.github.seggan.metis.runtime.chunk.Insn

enum class BinOp(internal val generateCode: InsnsBuilder.(List<FullInsn>, List<FullInsn>) -> Unit) {
    PLUS("__plus__"),
    MINUS("__minus__"),
    TIMES("__times__"),
    DIV("__div__"),
    MOD("__mod__"),
    POW("__pow__"),
    RANGE("__range__"),
    IN({ left, right ->
        +right
        +left
        generateMetaCall("__contains__", 1)
    }),
    NOT_IN(IN),
    IS({ left, right ->
        +left
        +right
        +Insn.Is
    }),
    IS_NOT(IS),
    EQ("__eq__"),
    NOT_EQ(EQ),
    LESS(-1),
    LESS_EQ(1, true),
    GREATER(1),
    GREATER_EQ(-1, true),
    AND({ left, right ->
        +left
        val end = Insn.Label()
        +Insn.RawJumpIf(end, condition = false, consume = false)
        +Insn.Pop
        +right
        +end
    }),
    OR({ left, right ->
        +left
        val end = Insn.Label()
        +Insn.RawJumpIf(end, condition = true, consume = false)
        +Insn.Pop
        +right
        +end
    }),
    ELVIS({ left, right ->
        +left
        +Insn.Push(Value.Null)
        +Insn.CopyUnder(1)
        generateMetaCall("__eq__", 1)
        val end = Insn.Label()
        +Insn.RawJumpIf(end, condition = false)
        +Insn.Pop
        +right
        +end
    });

    constructor(metamethod: String) : this({ left, right ->
        +left
        +right
        generateMetaCall(metamethod, 1)
    })

    constructor(op: BinOp) : this({ left, right ->
        op.generateCode(this, left, right)
        +Insn.Not
    })

    constructor(number: Int, inverse: Boolean = false) : this({ left, right ->
        +left
        +right
        generateMetaCall("__cmp__", 1)
        +Insn.Push(number)
        +Insn.CopyUnder(1)
        +Insn.Push("__eq__")
        +Insn.Index
        +Insn.Call(2)
        if (inverse) {
            +Insn.Not
        }
    })
}

enum class UnOp {
    NOT,
    NEG,
}