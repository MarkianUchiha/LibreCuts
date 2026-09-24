#!/usr/bin/env node
/**
 * Corre los tests instrumentados en la tablet sin borrar los datos de la app.
 *
 * `connectedAndroidTest` desinstala la app antes de correr, y con ella se van el proyecto abierto
 * y los archivos de cacheDir de los que depende. Este script hace el ciclo a mano:
 * compilar → instalar con `-r` (reemplazar, conservando datos) → `am instrument`.
 *
 *   node scripts/test-tablet.mjs                          # toda la suite
 *   node scripts/test-tablet.mjs TransitionPreviewGeometryTest   # una clase (basta el nombre)
 *   node scripts/test-tablet.mjs --skip-build             # reusa los APK ya compilados
 *   ANDROID_SERIAL=<serial> node scripts/test-tablet.mjs  # con más de un dispositivo conectado
 */

import { execFileSync, spawnSync } from 'node:child_process'
import { existsSync, readFileSync, readdirSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = dirname(dirname(fileURLToPath(import.meta.url)))
const APP_ID = 'com.tharunbirla.librecuts'
const RUNNER = `${APP_ID}.test/androidx.test.runner.AndroidJUnitRunner`
const APK_DIR = join(ROOT, 'app/build/outputs/apk')

const args = process.argv.slice(2)
const skipBuild = args.includes('--skip-build')
const filter = args.find((a) => !a.startsWith('--'))

function fail(msg) {
  console.error(`\n✗ ${msg}`)
  process.exit(1)
}

/** El adb del SDK que declara local.properties; el PATH de Windows no suele traerlo. */
function findAdb() {
  const props = join(ROOT, 'local.properties')
  if (!existsSync(props)) fail('falta local.properties con sdk.dir')
  const sdk = readFileSync(props, 'utf8').match(/^sdk\.dir=(.+)$/m)?.[1]?.trim()
  if (!sdk) fail('local.properties no declara sdk.dir')
  const adb = join(sdk.replace(/\\\\/g, '\\').replace(/\\:/g, ':'), 'platform-tools', process.platform === 'win32' ? 'adb.exe' : 'adb')
  if (!existsSync(adb)) fail(`no encuentro adb en ${adb}`)
  return adb
}

const adb = findAdb()
const sh = (cmd, cmdArgs, opts = {}) =>
  execFileSync(cmd, cmdArgs, { cwd: ROOT, encoding: 'utf8', maxBuffer: 64 * 1024 * 1024, ...opts })

const devices = sh(adb, ['devices'])
  .split('\n')
  .slice(1)
  .filter((l) => l.trim().endsWith('\tdevice'))
// Con la tablet y el teléfono conectados, ANDROID_SERIAL elige uno; adb lo lee solo en cada comando.
const serial = process.env.ANDROID_SERIAL
if (serial && !devices.some((l) => l.startsWith(`${serial}\t`))) fail(`ANDROID_SERIAL=${serial} no está conectado`)
if (devices.length === 0) fail('no hay dispositivo conectado (¿depuración USB activa?)')
if (devices.length > 1 && !serial) fail(`hay ${devices.length} dispositivos conectados; elige uno con ANDROID_SERIAL=<serial>`)

if (!skipBuild) {
  console.log('▸ compilando app y tests...')
  const gradlew = process.platform === 'win32' ? 'gradlew.bat' : './gradlew'
  const build = spawnSync(join(ROOT, gradlew), ['assembleDebug', 'assembleDebugAndroidTest'], {
    cwd: ROOT,
    encoding: 'utf8',
    shell: process.platform === 'win32',
  })
  if (build.status !== 0) {
    // Solo los errores: la salida completa de Gradle entierra la línea que importa.
    const errores = (build.stdout + build.stderr).split('\n').filter((l) => l.startsWith('e: ') || l.includes('FAILED'))
    console.error(errores.join('\n') || build.stderr)
    fail('la compilación falló')
  }
}

/** El APK de la app viene partido por ABI (splits.abi); hay que elegir el del dispositivo. */
function appApk() {
  const abi = sh(adb, ['shell', 'getprop', 'ro.product.cpu.abi']).trim()
  const dir = join(APK_DIR, 'debug')
  if (!existsSync(dir)) fail('no hay APK compilado; corre sin --skip-build')
  const apks = readdirSync(dir).filter((f) => f.endsWith('.apk'))
  const match = apks.find((f) => f.includes(abi)) ?? apks.find((f) => f.includes('universal')) ?? apks[0]
  if (!match) fail(`no hay APK para la ABI ${abi} en ${dir}`)
  return join(dir, match)
}

function testApk() {
  const dir = join(APK_DIR, 'androidTest/debug')
  const match = readdirSync(dir).find((f) => f.endsWith('.apk'))
  if (!match) fail('no hay APK de tests; corre sin --skip-build')
  return join(dir, match)
}

for (const apk of [appApk(), testApk()]) {
  console.log(`▸ instalando ${apk.split(/[/\\]/).pop()}`)
  // -r reemplaza conservando los datos; -t permite APK marcados como de prueba.
  sh(adb, ['install', '-r', '-t', apk])
}

const instrument = ['shell', 'am', 'instrument', '-w']
if (filter) {
  // Basta el nombre de la clase: se completa el paquete buscándola en el código.
  const clase = filter.includes('.') ? filter : buscarClase(filter)
  instrument.push('-e', 'class', clase)
  console.log(`▸ corriendo ${clase}`)
} else {
  instrument.push('-e', 'package', APP_ID)
  console.log('▸ corriendo la suite completa')
}
instrument.push(RUNNER)

function buscarClase(nombre) {
  const base = join(ROOT, 'app/src/androidTest/java')
  const encontrar = (dir) => {
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      const p = join(dir, entry.name)
      if (entry.isDirectory()) {
        const hit = encontrar(p)
        if (hit) return hit
      } else if (entry.name === `${nombre}.kt` || entry.name === `${nombre}.java`) {
        return readFileSync(p, 'utf8').match(/^package\s+([\w.]+)/m)?.[1] + `.${nombre}`
      }
    }
    return null
  }
  const clase = encontrar(base)
  if (!clase) fail(`no encontré la clase ${nombre} en androidTest`)
  return clase
}

const run = spawnSync(adb, instrument, { cwd: ROOT, encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 })
const salida = run.stdout + run.stderr

const ok = salida.match(/^OK \((\d+) tests?\)/m)
if (ok) {
  console.log(`\n✓ ${ok[1]} tests en verde`)
  process.exit(0)
}

// Las aserciones traen 20 líneas de stack de JUnit que no dicen nada; deja solo el mensaje.
const fallos = salida.split('\n').filter((l) => /^(\d+\)|.*(AssertionError|Tests run|FAILURES|Error in|Process crashed))/.test(l))
console.error(fallos.join('\n') || salida.slice(-2000))
fail('hay tests en rojo')
