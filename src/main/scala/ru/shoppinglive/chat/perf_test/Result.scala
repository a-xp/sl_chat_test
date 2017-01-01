package ru.shoppinglive.chat.perf_test

/**
  * Created by rkhabibullin on 23.12.2016.
  */
sealed trait Result

object Result {
  case class Msg(text:String, time:Long, from:Int)

  case class AuthSuccessResult(role: String, roleName: String, login:String, id:Int) extends Result
  case class AuthFailedResult(reason:String) extends Result
  case class GroupInfo(id:Int, name:String)
  case class GroupsResult(groups:Seq[GroupInfo]) extends Result
  case class ContactInfo(dlgId:Int, userId:Int, login: String, hasNew: Boolean, last: Long, userName:String, userLast:String)
  case class ContactChanges(dlgId:Int, userId:Int, hasNew:Boolean, last:Long)
  case class ContactsResult(contacts: Seq[ContactInfo]) extends Result
  case class DialogIdResult(withWhom:Int, dlgId:Int) extends Result
  case class ContactUpdate(contact: ContactChanges) extends Result
  case class DialogMsgAccepted(dlgId:Int, hash:Int, time:Long) extends Result
  case class DialogNewMsg(dlgId:Int, msg:Seq[Msg]) extends Result
  case class DialogMsgList(dlgId:Int, msg:Seq[Msg], total:Int, from:Int, to:Int) extends Result
  case class TypingNotification(dlgId:Int, who:Int) extends Result
}
