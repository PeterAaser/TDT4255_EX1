package Ov1
import chisel3.iotesters._
import scala.collection.mutable.LinkedHashMap
import spire.math.{UInt => Uint}
import spire.implicits._

import RISCVutils._
import assembler._
import spire.math.{UInt => Uint}

class TestRunner(program: RISCVProgram, init: MachineState, stepsTimeOut: Int, c: Tile, verbose: Boolean = false) {

  val missThreshhold = 10

  def prettyPeek(peek: LinkedHashMap[String, BigInt]): String = {
    peek.toList.map{ case(sig, value) =>
      s"$sig\t <- $value"
    }.mkString("\n","\n","\n")
  }


  def checkMemUpdate(d: PeekPokeTester[Tile],
                     expected: List[(Addr, Word)]): Either[String, List[(Addr, Word)]] = {

    if(d.peek(d.dut.io.memDeviceWriteEnable) == 1){
      if(expected.isEmpty) {
        Left("Unexpected memory write. (Emulator recorded less writes than your design)")
      }
      else {
        val memWriteAddress         = d.peek(d.dut.io.memDeviceWriteAddress)
        val memWriteAddressErrorMsg = s"Attempted to write to address $memWriteAddress. Expected was ${expected.head._1.toBigInt}"
        val memWriteData            = d.peek(d.dut.io.memDeviceWriteData)
        val memWriteDataErrorMsg    = s"Attempted to write wrong data to address $memWriteAddress. Written was ${memWriteData}, Expected data was ${expected.head._1.toBigInt}"

        d.expect(d.dut.io.memDeviceWriteAddress, expected.head._1.toBigInt, memWriteAddressErrorMsg)
        d.expect(d.dut.io.memDeviceWriteData, expected.head._2.toBigInt, memWriteDataErrorMsg)
        Right(expected.tail)
      }
    }
    else
      Right(expected)
  }

  def checkRegUpdate(d: PeekPokeTester[Tile],
                     expected: List[(Reg, Word)]): Either[String, List[(Reg, Word)]] = {

    if(d.peek(d.dut.io.regsDeviceWriteEnable) == 1){
      if(expected.isEmpty) {
        Left("Unexpected register write. (Emulator recorded less writes than your design)")
      }
      else {

        val regWriteAddress         = d.peek(d.dut.io.regsDeviceWriteAddress)
        val regWriteAddressErrorMsg = s"Attempted to write to address $regWriteAddress. Expected was ${expected.head._1.toBigInt}"
        val regWriteData            = d.peek(d.dut.io.regsDeviceWriteData)
        val regWriteDataErrorMsg    = s"Attempted to write wrong data to address $regWriteAddress. Written was ${regWriteData}, Expected data was ${expected.head._1.toBigInt}"

        d.expect(d.dut.io.regsDeviceWriteAddress, expected.head._1.toBigInt, regWriteAddressErrorMsg)
        d.expect(d.dut.io.regsDeviceWriteData, expected.head._2.toBigInt, regWriteDataErrorMsg)
        Right(expected.tail)
      }
    }
    else
      Right(expected)
  }


  def checkPCUpdate(d: PeekPokeTester[Tile],
                    pcLog: List[Addr],
                    pcTrace: List[Addr],
                    misses: Int): Either[String, (List[Addr], List[Addr], Int)] = {

    val devicePc = Uint(d.peek(d.dut.io.currentPC).toInt)
    val (nextPcLog, nextPcTrace, nextMisses) = pcLog match {
      case h :: t =>
        if(devicePc == h)
          (t, devicePc :: pcTrace, 0)
        else
          (pcLog, devicePc :: pcTrace, misses + 1)
      case Nil => (pcLog, devicePc :: pcTrace, misses + 1)
    }

    if(nextMisses > missThreshhold) {
      val pcString = pcTrace.take(missThreshhold*2).reverse.mkString("\n","\n","\n")
      Left(s"PC derailed! Last ${missThreshhold} device PC trace: $pcString")
    }
    else
      Right((nextPcTrace, nextPcTrace, nextMisses))
  }


  def stepOne(
    expectedRegUpdates: List[(Reg, Word)],
    expectedMemUpdates: List[(Addr, Word)],
    pcTrace: List[Addr] = List[Addr](),
    pcLog: List[Addr],
    misses: Int,
    d: PeekPokeTester[Tile]
  ): Either[String, (List[(Reg,Word)], List[(Addr,Word)], List[Addr], List[Addr], Int)] = {

    for {
      nextRegs <- checkRegUpdate(d, expectedRegUpdates)
      nextMem  <- checkMemUpdate(d, expectedMemUpdates)
      (nextPClog, nextPCtrace, nextMisses) <- checkPCUpdate(d, pcLog, pcTrace, misses)
    } yield (nextRegs, nextMem, nextPClog, nextPCtrace, nextMisses)
  }


  def run = new PeekPokeTester(c){

    def setup(instructions: List[Uint], regs: List[(Reg, Word)], mem: List[(Addr, Word)], d: PeekPokeTester[Tile]) = {
      regs.foreach{ case(reg, word) =>
        d.poke(d.dut.io.setup, 1)
        d.poke(d.dut.io.running, 0)
        d.poke(d.dut.io.checkResult, 0)
        d.poke(d.dut.io.regsWriteEnable, 1)
        d.poke(d.dut.io.regsWriteData, BigInt(word.toInt))
        d.poke(d.dut.io.regsAddress, reg)
        step(1)
      }

      mem.foreach { case (addr, word) =>
        d.poke(d.dut.io.setup, 1)
        d.poke(d.dut.io.running, 0)
        d.poke(d.dut.io.checkResult, 0)
        d.poke(d.dut.io.DMEMWriteEnable, 1)
        d.poke(d.dut.io.DMEMWriteData, addr.toInt)
        d.poke(d.dut.io.DMEMAddress, word.toInt)
        step(1)
      }

      for( ii <- 0 until instructions.length) {
        d.poke(d.dut.io.setup, 1)
        d.poke(d.dut.io.running, 0)
        d.poke(d.dut.io.checkResult, 0)
        d.poke(d.dut.io.IMEMAddress, ii*4)
        d.poke(d.dut.io.IMEMWriteData, instructions(ii).toInt)
        step(1)
      }

      d.poke(d.dut.io.setup, 0)
      d.poke(d.dut.io.running, 1)
    }

    def stepMany(
      timeOut: Int,
      expectedRegUpdates: List[(Reg, Word)],
      expectedMemUpdates: List[(Addr, Word)],
      expectedPCupdates: List[Addr],
      pcTrace: List[Addr],
      misses: Int,
      finishLine: Int, // passed as int due to an asinine scala/chisel type error.
      d: PeekPokeTester[Tile]
    ): Unit = {

      if(timeOut == 0) {
        println("Looks like you're out of time")
        d.fail
      }
      else if(Uint(d.peek(d.dut.io.currentPC).toInt) == Uint(finishLine)){
        if(expectedMemUpdates.isEmpty && expectedRegUpdates.isEmpty) {
          println("You're winner!")
        }
        else{
          println("Program terminated successfully, but expected reg/mem updates have not happened.")
          d.fail
        }
      }
      else {
        stepOne(expectedRegUpdates, expectedMemUpdates, pcUpdates, pcTrace, misses, d) match {
          case Right((nextReg, nextMem, nextPclog, nextPctrace, nextMisses)) =>
            step(1)
            stepMany(
              timeOut - 1,
              nextReg,
              nextMem,
              nextPclog,
              nextPctrace,
              nextMisses,
              finishLine,
              d)
          case Left(s) => { println(s); d.fail }
        }
      }
    }


    val log = program.execute(stepsTimeOut, init)

    val maxSteps = (log.opLog.size.toDouble*1.5).toInt

    val (regUpdates, memUpdates, pcUpdates) = log.getUpdateLog
    val initReg = log.getInitState.regs
    val initMem = log.getInitState.mem
    val machineOps = assembleProgram(program)

    if(verbose){
      println("This test is called with the verbose flag set to true")
      println("Verbose output as follows\n")
      println(log.getDescriptiveLog)
      println("\nVerbose output done\n\n")
    }

    setup(machineOps, initReg.toList, initMem.toList, this)
    stepMany(maxSteps,
             regUpdates,
             memUpdates,
             pcUpdates,
             List[Addr](),
             0,
             log.termination.right.get._2.toInt,
             this)
  }
}
