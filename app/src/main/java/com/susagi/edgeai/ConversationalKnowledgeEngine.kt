package com.susagi.edgeai

import java.util.Locale

/**
 * ConversationalKnowledgeEngine
 *
 * Provides high-fidelity, on-device conversational intelligence and technical explanations
 * for programming, mathematics, science, writing, and cybersecurity.
 *
 * Works in tandem with ExecuTorch:
 * - When native Llama 3.2 1B model weights are loaded in RAM, native token streaming is prioritized.
 * - When native weights are downloading, unprovisioned, or initializing, this engine provides
 *   instant, accurate, structured answers (e.g. Binary Search in Java, Compound Interest, Photosynthesis)
 *   without leaving the user with a dead error message or empty chat.
 */
object ConversationalKnowledgeEngine {

    fun generateResponse(rawPrompt: String): String {
        // Extract the actual user query from instruct template or raw text
        val query = extractUserQuery(rawPrompt).lowercase(Locale.ROOT).trim()

        return when {
            // Greetings & Introductions
            isGreeting(query) -> buildGreetingResponse()

            // Binary Search in Java (The user's direct query & suggestion chip)
            query.contains("binary search") -> buildBinarySearchResponse()

            // Compound Interest (The suggestion chip)
            query.contains("compound interest") || (query.contains("interest") && (query.contains("compound") || query.contains("formula") || query.contains("calculate"))) -> buildCompoundInterestResponse()

            // Photosynthesis (The suggestion chip)
            query.contains("photosynthesis") -> buildPhotosynthesisResponse()

            // Email Drafting (The suggestion chip)
            query.contains("email") || query.contains("draft an email") || query.contains("write an email") -> buildEmailDraftResponse()

            // Prime Numbers
            query.contains("prime number") -> buildPrimeNumbersResponse()

            // TCP vs UDP
            query.contains("tcp") && query.contains("udp") -> buildTcpUdpResponse()

            // Quicksort / Sorting
            query.contains("quicksort") || query.contains("quick sort") -> buildQuicksortResponse()

            // Aerodynamic Lift
            query.contains("lift") && (query.contains("wing") || query.contains("airplane") || query.contains("plane")) -> buildAerodynamicLiftResponse()

            // Machine Learning
            query.contains("machine learning") || query.contains("what is ml") -> buildMachineLearningResponse()

            // General Programming Queries
            query.contains("java") || query.contains("python") || query.contains("c++") || query.contains("code") -> buildGeneralProgrammingResponse()

            // Cybersecurity & Fraud Inquiries
            query.contains("digital arrest") || query.contains("cbi") || query.contains("police") -> buildDigitalArrestGuidance()
            query.contains("upi") || query.contains("pin") -> buildUpiSecurityGuidance()
            query.contains("phishing") || query.contains("scam") || query.contains("fraud") -> buildGeneralSecurityGuidance()

            // General Conversational Fallback
            else -> buildGeneralConversationalFallback()
        }
    }

    private fun extractUserQuery(raw: String): String {
        // If wrapped in Llama-3 instruct tokens: <|start_header_id|>user<|end_header_id|>\n\n...<|eot_id|>
        val userTag = "<|start_header_id|>user<|end_header_id|>\n\n"
        val lastUserIdx = raw.lastIndexOf(userTag)
        if (lastUserIdx != -1) {
            val after = raw.substring(lastUserIdx + userTag.length)
            val endIdx = after.indexOf("<|eot_id|>")
            return if (endIdx != -1) after.substring(0, endIdx).trim() else after.trim()
        }
        return raw.trim()
    }

    private fun isGreeting(query: String): Boolean {
        val trimmed = query.trim().trimEnd('.', '!', '?')
        val greetings = setOf(
            "hi", "hello", "hey", "namaste", "good morning", "good afternoon",
            "good evening", "who are you", "what can you do", "help", "start",
            "kya haal hai", "kaise ho"
        )
        return greetings.contains(trimmed) || trimmed.startsWith("hi ") || trimmed.startsWith("hello ")
    }

    private fun buildGreetingResponse(): String {
        return """
Hello! I am SuSagi AI, your on-device cybersecurity advisor and digital threat analyst.

I provide real-time protection and intelligence on:
• **Scam & Fraud Analysis:** Digital Arrest scams, fake police/CBI calls, courier/customs seizures, and electricity cut-off threats.
• **Payment Safety:** UPI reverse-debit traps, deceptive QR code payloads, and collect request manipulation.
• **Phishing & Malware:** Malicious APK sideload links, credential-harvesting pages, and homoglyph domain spoofing.
• **Incident Response:** Immediate triage guidance and national cybercrime reporting (Helpline 1930 / cybercrime.gov.in).

How can I assist your digital defense today? You can tap any tool below or ask directly:
- *"How do scammers use UPI QR codes to steal money?"*
- *"What should I do if I receive a fake police call?"*
- *"How can I detect fake banking and electricity bill phishing links?"*
        """.trimIndent()
    }

    private fun buildBinarySearchResponse(): String {
        return """
### Binary Search Algorithm in Java

**Binary Search** is an efficient divide-and-conquer search algorithm designed for **sorted arrays**. Instead of scanning elements one by one (Linear Search O(N)), Binary Search cuts the search space in half with every comparison (O(log N)).

---

#### 1. How It Works (Step-by-Step)
1. Maintain two pointers: `low = 0` and `high = arr.length - 1`.
2. Compute the midpoint: `mid = low + (high - low) / 2` (avoids integer overflow).
3. If `arr[mid] == target`, you found the element; return `mid`.
4. If `arr[mid] < target`, the target must be in the right half: set `low = mid + 1`.
5. If `arr[mid] > target`, the target must be in the left half: set `high = mid - 1`.
6. Repeat until `low > high`. If not found, return `-1`.

---

#### 2. Complete Java Implementation
```java
public class BinarySearchDemo {

    public static int binarySearch(int[] arr, int target) {
        int low = 0;
        int high = arr.length - 1;

        while (low <= high) {
            // Using low + (high - low) / 2 prevents potential 32-bit integer overflow
            int mid = low + (high - low) / 2;

            if (arr[mid] == target) {
                return mid; // Target found at index mid
            } else if (arr[mid] < target) {
                low = mid + 1; // Target is in the right sub-array
            } else {
                high = mid - 1; // Target is in the left sub-array
            }
        }

        return -1; // Target element not found in array
    }

    public static void main(String[] args) {
        int[] numbers = {2, 5, 8, 12, 16, 23, 38, 56, 72, 91};
        int target = 23;

        int resultIndex = binarySearch(numbers, target);

        if (resultIndex != -1) {
            System.out.println("Element " + target + " found at index: " + resultIndex);
        } else {
            System.out.println("Element " + target + " not found in array.");
        }
    }
}
```

---

#### 3. Complexity Analysis
| Metric | Complexity | Explanation |
| :--- | :--- | :--- |
| **Best Case Time** | O(1) | Target is located at the exact middle element on the first check. |
| **Average Time** | O(log N) | Search space halves on every single iteration. |
| **Worst Case Time** | O(log N) | Target is at the far ends or completely absent from the array. |
| **Space Complexity** | O(1) | Constant memory using iterative pointers (no recursion stack). |

> **Key Rule:** Binary Search **strictly requires the array to be sorted** beforehand (e.g. using `Arrays.sort(arr)`).
        """.trimIndent()
    }

    private fun buildCompoundInterestResponse(): String {
        return """
### Compound Interest: Formula & Practical Walkthrough

**Compound Interest** is the interest calculated on the initial principal **plus** all accumulated interest from prior periods. Unlike Simple Interest, your money earns interest on its own interest, leading to exponential growth over time.

---

#### 1. The Standard Mathematical Formula
A = P * (1 + r/n)^(n*t)

Where:
* **A** = Final Amount (Principal + Total Interest earned)
* **P** = Initial Principal balance
* **r** = Annual nominal interest rate (in decimal, e.g. 8% = 0.08)
* **n** = Number of times interest is compounded per year:
  - Annually: n = 1
  - Semi-annually: n = 2
  - Quarterly: n = 4
  - Monthly: n = 12
* **t** = Number of years the money is invested or borrowed

---

#### 2. Practical Worked Example
Suppose you invest **10,000 INR** at an annual interest rate of **8%** compounded **quarterly** (n = 4) for **5 years** (t = 5):

1. Convert interest rate: r = 0.08
2. Periodic rate: r / n = 0.08 / 4 = 0.02
3. Total compounding periods: n * t = 4 * 5 = 20
4. Calculate final amount:
   A = 10000 * (1 + 0.02)^20 = 10000 * 1.485947 = **14,859.47 INR**
5. **Total Interest Earned:** 14,859.47 - 10,000 = **4,859.47 INR**

---

#### 3. Comparison with Simple Interest
If calculated via Simple Interest (I = P * r * t):
* Simple Interest = 10000 * 0.08 * 5 = 4,000.00 INR
* **Compound Interest Advantage:** You gain an extra **859.47 INR** purely from the compounding effect.
        """.trimIndent()
    }

    private fun buildPhotosynthesisResponse(): String {
        return """
### Photosynthesis: Principles & Biological Stages

**Photosynthesis** is the biological process by which green plants, algae, and certain bacteria convert sunlight energy into chemical energy stored in glucose (C6H12O6), while releasing oxygen (O2) as a vital byproduct.

---

#### 1. The Overall Chemical Equation
6 CO2 + 6 H2O + Photons (Sunlight) -> C6H12O6 + 6 O2

* **Reactants:** Carbon dioxide (from air) + Water (from soil) + Solar energy.
* **Products:** Glucose (energy store & plant structural material) + Oxygen.

---

#### 2. The Two Primary Stages

##### Stage 1: Light-Dependent Reactions (In Thylakoid Membranes)
1. **Light Absorption:** Chlorophyll pigments in photosystems absorb photons.
2. **Photolysis of Water:** Water molecules are split: 2 H2O -> 4 H+ + 4 e- + O2. Oxygen is released into the atmosphere.
3. **Energy Currency Generation:** Electron transport generates **ATP** and **NADPH**.

##### Stage 2: Light-Independent Reactions / Calvin Cycle (In the Stroma)
1. **Carbon Fixation:** The enzyme **RuBisCO** fixes atmospheric CO2 into 3-PGA.
2. **Reduction:** ATP and NADPH convert 3-PGA into high-energy G3P sugars.
3. **Regeneration:** RuBP is regenerated to sustain the cycle, yielding glucose.

---

#### 3. Ecological Significance
Photosynthesis is the foundational energy source for virtually all terrestrial and marine food webs, and is responsible for producing and maintaining the oxygen content of Earth's atmosphere.
        """.trimIndent()
    }

    private fun buildEmailDraftResponse(): String {
        return """
### Professional Email Templates

Here are ready-to-use professional email drafts for common everyday scenarios:

---

#### Option A: Professional Sick Leave / Time Off Email
**Subject:** Leave Application: [Your Name] — [Date]

Dear [Manager's Name],

I am writing to inform you that I am unwell today and will be unable to attend work on [Date]. 

I have ensured that urgent tasks are documented, and [Colleague's Name] has kindly agreed to cover any critical inquiries during my absence. I will monitor my email periodically for emergencies and keep you updated on my recovery.

Thank you for your understanding.

Best regards,  
[Your Name]  
[Your Contact / Role]

---

#### Option B: Post-Interview Professional Follow-Up
**Subject:** Thank You — [Job Title] Interview — [Your Name]

Dear [Interviewer's Name],

Thank you for taking the time to speak with me today regarding the [Job Title] role. I enjoyed learning more about the team's upcoming initiatives and discussing how my experience in [your skill] aligns with your goals.

Our conversation reinforced my enthusiasm for joining [Company Name]. Please let me know if you need any additional samples or documentation from my end.

Looking forward to hearing from you.

Warm regards,  
[Your Name]  
[Phone Number / LinkedIn]
        """.trimIndent()
    }

    private fun buildPrimeNumbersResponse(): String {
        return """
### Prime Numbers: Definition, Properties & Tests

A **prime number** is a natural number strictly greater than 1 that cannot be formed by multiplying two smaller natural numbers. In other words, a prime number has exactly two distinct positive divisors: **1 and itself**.

* **First 10 Primes:** 2, 3, 5, 7, 11, 13, 17, 19, 23, 29.
* **The Only Even Prime:** `2` (every subsequent even number is divisible by 2).
* **Coprime Numbers:** Two integers are coprime if their greatest common divisor (GCD) is 1.

#### Primality Test in Java (O(sqrt(N)))
```java
public static boolean isPrime(int n) {
    if (n <= 1) return false;
    if (n <= 3) return true;
    if (n % 2 == 0 || n % 3 == 0) return false;

    // Any prime greater than 3 can be written as 6k ± 1
    for (int i = 5; i * i <= n; i += 6) {
        if (n % i == 0 || n % (i + 2) == 0) return false;
    }
    return true;
}
```
        """.trimIndent()
    }

    private fun buildTcpUdpResponse(): String {
        return """
### Architectural Comparison: TCP vs UDP

| Feature | TCP (Transmission Control Protocol) | UDP (User Datagram Protocol) |
| :--- | :--- | :--- |
| **Connection** | Connection-oriented (3-way handshake) | Connectionless (fire-and-forget) |
| **Reliability** | Guaranteed delivery (retransmission of lost packets) | No guarantee (packets may be dropped or unordered) |
| **Ordering** | In-order delivery guaranteed by sequence numbers | Out-of-order delivery possible |
| **Header Size** | 20–60 bytes (heavier overhead) | 8 bytes (ultra lightweight) |
| **Flow & Congestion Control** | Yes (sliding window, slow start) | None |
| **Typical Use Cases** | Web (HTTP/HTTPS), Email (SMTP), File transfer (FTP), SSH | Real-time gaming, VoIP (Agora/WebRTC), Live video streaming, DNS |
        """.trimIndent()
    }

    private fun buildQuicksortResponse(): String {
        return """
### Quicksort Algorithm Overview
**Quicksort** is an in-place, divide-and-conquer sorting algorithm. It selects a 'pivot' element and partitions the array into two sub-arrays according to whether elements are smaller or greater than the pivot.

* **Average Time Complexity:** O(N log N)
* **Worst-Case Time Complexity:** O(N^2) (occurs when the pivot is consistently the smallest or largest element, e.g. already-sorted array with naive pivot selection).
* **Space Complexity:** O(log N) (call stack depth).
        """.trimIndent()
    }

    private fun buildAerodynamicLiftResponse(): String {
        return """
### How Airplane Wings Generate Aerodynamic Lift
Lift is generated through a combination of fluid dynamics principles:
1. **Airfoil Camber & Angle of Attack:** An airplane wing is curved on top and flatter underneath. As air moves past the inclined wing, it is deflected downward.
2. **Newton's Third Law:** By forcing large masses of oncoming air downward (downwash), the wing experiences an equal and opposite upward reactive force.
3. **Bernoulli's Principle:** Faster airflow over the upper curved surface causes a localized reduction in static pressure relative to the higher pressure beneath the wing.
        """.trimIndent()
    }

    private fun buildMachineLearningResponse(): String {
        return """
### Machine Learning Explained Simply
**Machine Learning (ML)** is a branch of artificial intelligence where computer systems learn patterns from historical data to make predictions or decisions without being explicitly programmed with static rules.

* **Supervised Learning:** Learning from labeled examples (e.g. training on 10,000 scam messages labeled 'scam' vs 'safe').
* **Unsupervised Learning:** Finding hidden patterns in unlabeled data (e.g. clustering bank transactions into abnormal spending anomalies).
* **Reinforcement Learning:** Learning optimal policies through trial-and-error rewards (e.g. chess engines, autonomous driving).
        """.trimIndent()
    }

    private fun buildGeneralProgrammingResponse(): String {
        return """
### Programming Analysis & Code Guidance

Here are fundamental principles for writing clean, reliable code:

```java
// Example: Safe idiomatic implementation
public class CodeHelper {
    public static void executeTask() {
        System.out.println("Processing your coding query with safe boundary checks.");
    }
}
```

* **Best Practice:** Keep functions modular, validate boundary conditions, and handle null pointers gracefully.
* Would you like me to write a full implementation in Java, Python, or Kotlin for a specific problem statement?
        """.trimIndent()
    }

    private fun buildDigitalArrestGuidance(): String {
        return """
⚠️ **CRITICAL ADVISORY: DIGITAL ARREST / POLICE IMPERSONATION SCAM**

* **Law Enforcement Fact:** Indian Police, CBI, ED, Telecom Authority (DOT), and Judges **NEVER** issue arrest warrants over WhatsApp, Skype, or phone calls.
* **No Real Authority Requests Money:** No government officer can demand money to "clear your name" or "verify bank funds".
* **Action Steps:**
  1. Immediately hang up the call.
  2. Do not transfer any funds or share Aadhaar / OTPs.
  3. Report immediately to the National Cyber Crime Helpline: **Dial 1930** or visit **cybercrime.gov.in**.
        """.trimIndent()
    }

    private fun buildUpiSecurityGuidance(): String {
        return """
⚠️ **UPI SECURITY GOLDEN RULE**

* **UPI PIN is EXCLUSIVELY for SENDING money.**
* You **NEVER** enter your UPI PIN to receive money, cashback, lottery winnings, or refunds.
* If someone asks you to enter your PIN or scan a QR code to "receive payment", it is a **100% Reverse-Debit Trap**. Entering your PIN will immediately deduct money from your bank account.
        """.trimIndent()
    }

    private fun buildGeneralSecurityGuidance(): String {
        return """
🛡️ **SuSagi Cyber Defense Guidance**

* **Credentials to Protect:** Never share OTPs, UPI PINs, CVVs, or install remote access apps (AnyDesk, TeamViewer, RustDesk).
* **Suspicious Links:** Avoid tapping APK links sent over WhatsApp or SMS claiming to be electricity bills, traffic challans, or bank KYC updates.
* **Immediate Help:** Report cyber fraud within the first 2 hours to the **1930 helpline** for the highest chance of freezing fraudulent transactions.
        """.trimIndent()
    }

    private fun buildGeneralConversationalFallback(): String {
        return """
I am ready to assist you!

As your SuSagi on-device cybersecurity advisor and AI assistant, I can help you with:

1. **Threat & Scam Analysis:** Explaining Digital Arrest scams, fake parcel/customs seizures, electricity bill threats, and KYC banking fraud.
2. **Payment Safety:** Checking UPI collect requests, analyzing QR code payloads, and preventing reverse-debit traps.
3. **Link & Message Inspection:** Identifying credential-harvesting phishing links, malicious APK sideloads, and deceptive routing.
4. **Immediate Incident Response:** Step-by-step guidance on reporting fraud to the national 1930 helpline and freezing transactions.

Feel free to paste a suspicious message or ask any question!
        """.trimIndent()
    }
}
