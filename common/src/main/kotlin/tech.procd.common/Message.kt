package tech.procd.common

interface Message

interface Event : Message
interface Command : Message
interface Reply : Event
