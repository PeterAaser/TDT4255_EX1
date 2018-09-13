package Ov1

import chisel3._
import chisel3.util._
import chisel3.core.Input
import chisel3.iotesters.PeekPokeTester


// From RISC-V reference card
object ALUOps {

  val ADD    = 0.U(4.W)
  val SUB    = 1.U(4.W)
  val AND    = 2.U(4.W)
  val OR     = 3.U(4.W)
  val XOR    = 4.U(4.W)
  val SLT    = 5.U(4.W)
  val SLL    = 6.U(4.W)
  val SLTU   = 7.U(4.W)
  val SRL    = 8.U(4.W)
  val SRA    = 9.U(4.W)
  val COPY_A = 10.U(4.W)
  val COPY_B = 11.U(4.W)

  val DC     = 15.U(4.W)
}

object lookup {
  def BEQ                = BitPat("b?????????????????000?????1100011")
  def BNE                = BitPat("b?????????????????001?????1100011")
  def BLT                = BitPat("b?????????????????100?????1100011")
  def BGE                = BitPat("b?????????????????101?????1100011")
  def BLTU               = BitPat("b?????????????????110?????1100011")
  def BGEU               = BitPat("b?????????????????111?????1100011")
  def JALR               = BitPat("b?????????????????000?????1100111")
  def JAL                = BitPat("b?????????????????????????1101111")
  def LUI                = BitPat("b?????????????????????????0110111")
  def AUIPC              = BitPat("b?????????????????????????0010111")
  def ADDI               = BitPat("b?????????????????000?????0010011")
  def SLLI               = BitPat("b000000???????????001?????0010011")
  def SLTI               = BitPat("b?????????????????010?????0010011")
  def SLTIU              = BitPat("b?????????????????011?????0010011")
  def XORI               = BitPat("b?????????????????100?????0010011")
  def SRLI               = BitPat("b000000???????????101?????0010011")
  def SRAI               = BitPat("b010000???????????101?????0010011")
  def ORI                = BitPat("b?????????????????110?????0010011")
  def ANDI               = BitPat("b?????????????????111?????0010011")
  def ADD                = BitPat("b0000000??????????000?????0110011")
  def SUB                = BitPat("b0100000??????????000?????0110011")
  def SLL                = BitPat("b0000000??????????001?????0110011")
  def SLT                = BitPat("b0000000??????????010?????0110011")
  def SLTU               = BitPat("b0000000??????????011?????0110011")
  def XOR                = BitPat("b0000000??????????100?????0110011")
  def SRL                = BitPat("b0000000??????????101?????0110011")
  def SRA                = BitPat("b0100000??????????101?????0110011")
  def OR                 = BitPat("b0000000??????????110?????0110011")
  def AND                = BitPat("b0000000??????????111?????0110011")
  def LW                 = BitPat("b?????????????????010?????0000011")
  def SW                 = BitPat("b?????????????????010?????0100011")
}
