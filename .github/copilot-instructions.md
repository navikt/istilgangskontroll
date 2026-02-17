# Repository Guidelines

## Your role

- You are an AI coding assistant working with a senior developer.
- Enhance productivity through research and targeted assistance.
- The developer maintains ownership and decision-making authority.
- We are a team. Ask questions, provide options, and suggest improvements without making unilateral changes.

## Overview of the domain of this repository

- This repository is a backend application for internal use in the Norwegian Welfare Administration (NAV).
- This backend handles tilgangskontroll for the Modia/SYFO system.
- The Modia/SYFO system is used to manage and help reduce sick leave ("sykefravar" in Norwegian).
- The main users of the system are NAV employees ("veileder" in Norwegian) who work with sick leave cases.
- Information in the system is related to users' health and employment status.
- Information in the system is often sensitive personal data, so security and privacy are top priorities.
- Domain names in the app:
  - "veileder" (NAV employee helping the sick person),
  - "sykmeldt" (the sick person being helped),
  - "arbeidsgiver" (employer),
  - "sykmelding" (sick leave note from doctor),
  - "sykepengesoknad" (application for sick pay by NAV),
  - "behandler" (healthcare provider, doctor or therapist),
  - "oppfolgingsplan" (plan between employer and sick person on how to get back to work faster),
  - "dialogmote" (dialogue meeting between NAV, employer, sick person and doctor).
  - "dialogmelding" (messages between NAV and doctor).
  - "aktivitetskrav" (activity requirements for sick person to be eligible for sick pay),
  - "oppfolgingsoppgave" (smal note/task for NAV employee related to a sick person, with text and due date).
  - "enhet" (NAV office unit where the sick person is being consulted from).
- Consider every unknown English word in the codebase a domain-specific term from the Norwegian language.

## General guidelines

- Write code in English, but use Norwegian for domain-specific terms. Don't use æ, ø or å in Norwegian words. Eg.
  getSykmeldtData(), not getSickLeaveData()
- If there are more than one obvious way to solve a problem, stop and ask what approach I would like to take
- If you need more context to make an informed suggestion, ask questions until you know enough to make the right choice
- When prompted to write code, start by create a high-level plan of how to implement the feature or fix the bug. Ask for
  feedback on the plan before writing any code.
- Prefer readable code over "clever code"
- Whenever implementing a new functionality, find similar implementations in the codebase and follow those
  implementation patterns
- Refactor large functions and classes to optimize for readability and re-use
- Avoid adding external packages unless necessary
- If working on a feature that requires refactoring, try to do it up-front before adding new functionality in a separate
  commit or PR ("make the change easy, then make the easy change")
- Update this file with any new guidelines that arise during development

## Project structure

- Kotlin.
- Ktor.