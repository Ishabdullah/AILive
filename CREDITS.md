# AILive Model Credits & Licenses

This document lists all AI models used in AILive, their licenses, and attribution requirements.

---

## ✅ All Models Are Commercial-Use Friendly

Every model in AILive uses either **MIT License** or **Apache 2.0 License**, both of which explicitly permit commercial use without restrictions.

---

## 📦 Models Used

### 1. SmolLM2-360M (Q4_K_M quantized)

**Purpose:** Language understanding, reasoning, planning  
**Agent:** Meta AI (Orchestrator)  
**License:** Apache License 2.0  
**Source:** Hugging Face (HuggingFaceTB/SmolLM2-360M)  
**Size:** 271 MB (quantized)  
**Original Size:** 720 MB (fp16)

**Attribution:**
SmolLM2 by Hugging Face
Licensed under Apache License 2.0
https://huggingface.co/HuggingFaceTB/SmolLM2-360M

**Commercial Use:** ✅ **YES - Fully Permitted**

**License Details:** Apache 2.0 allows:
- Commercial use
- Modification
- Distribution
- Patent use
- Private use

**Requirements:**
- Include copy of Apache 2.0 license
- State significant changes made
- Include copyright notice

---

### 2. Whisper-Tiny (int8 quantized)

**Purpose:** Automatic speech recognition  
**Agent:** Language AI  
**License:** MIT License  
**Source:** OpenAI  
**Size:** 145 MB (int8 quantized)  
**Original Size:** 244 MB (fp32)

**Attribution:**
Whisper by OpenAI
Licensed under MIT License
https://github.com/openai/whisper

**Commercial Use:** ✅ **YES - Fully Permitted**

**License Details:** MIT allows:
- Commercial use
- Modification
- Distribution
- Private use

**Requirements:**
- Include copy of MIT license
- Include copyright notice: "Copyright (c) 2022 OpenAI"

---

### 3. MobileNetV3-Small

**Purpose:** Object detection, image classification  
**Agent:** Visual AI  
**License:** Apache License 2.0  
**Source:** Google Research / TensorFlow  
**Size:** 10 MB

**Attribution:**
MobileNetV3 by Google Research
Licensed under Apache License 2.0
https://github.com/tensorflow/models/tree/master/research/slim/nets/mobilenet

**Commercial Use:** ✅ **YES - Fully Permitted**

**License Details:** Same as SmolLM2 (Apache 2.0)

**Requirements:**
- Include copy of Apache 2.0 license
- Include copyright notice

---

### 4. BGE-small-en-v1.5

**Purpose:** Text embeddings for semantic search  
**Agent:** Memory AI  
**License:** MIT License  
**Source:** BAAI (Beijing Academy of Artificial Intelligence)  
**Size:** 133 MB  
**Dimensions:** 384

**Attribution:**
BGE (BAAI General Embedding) by Beijing Academy of Artificial Intelligence
Licensed under MIT License
https://huggingface.co/BAAI/bge-small-en-v1.5

**Commercial Use:** ✅ **YES - Fully Permitted**

**Why BGE over MiniLM:**
- MiniLM-L6-v2 trained on MS MARCO (non-commercial license)
- BGE-small trained on commercially-licensed datasets only
- BGE-small has better performance
- 100% legal clarity for commercial use

**License Details:** MIT (same as Whisper)

**Requirements:**
- Include copy of MIT license
- Include copyright notice

---

### 5. DistilBERT (Base Uncased, fine-tuned for sentiment)

**Purpose:** Sentiment analysis, emotion detection  
**Agent:** Emotion AI  
**License:** Apache License 2.0  
**Source:** Hugging Face / distilbert-base-uncased  
**Size:** 127 MB  
**Fine-tune:** distilbert-base-uncased-finetuned-sst-2-english

**Attribution:**
DistilBERT by Hugging Face
Licensed under Apache License 2.0
https://huggingface.co/distilbert-base-uncased

**Commercial Use:** ✅ **YES - Fully Permitted**

**Training Data:**
- Wikipedia (public domain)
- BookCorpus (public domain)
- SST-2 dataset (permissive license)

**License Details:** Apache 2.0

**Requirements:**
- Include copy of Apache 2.0 license
- Include copyright notice

---

## 📋 License Summary

| Model | License | Commercial OK? | Attribution Required? |
|-------|---------|----------------|----------------------|
| SmolLM2-360M | Apache 2.0 | ✅ YES | ✅ YES |
| Whisper-Tiny | MIT | ✅ YES | ✅ YES |
| MobileNetV3-Small | Apache 2.0 | ✅ YES | ✅ YES |
| BGE-small-en-v1.5 | MIT | ✅ YES | ✅ YES |
| DistilBERT | Apache 2.0 | ✅ YES | ✅ YES |

**Total:** 5 models, **100% commercial-safe**

---

## 📜 Full License Texts

### MIT License (Whisper, BGE-small)
MIT LicensePermission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

### Apache License 2.0 (SmolLM2, MobileNetV3, DistilBERT)
Apache License
Version 2.0, January 2004
http://www.apache.org/licenses/Full text available at: https://www.apache.org/licenses/LICENSE-2.0Summary:Commercial use: ALLOWEDModification: ALLOWEDDistribution: ALLOWEDPatent use: ALLOWEDPrivate use: ALLOWEDRequirements:Include copy of licenseState significant changesInclude copyright and attribution noticesInclude NOTICE file if provided

---

## 🤝 Contributing Your Own Models

If you want to add a model to AILive:

**Acceptable Licenses:**
- ✅ MIT License
- ✅ Apache 2.0
- ✅ BSD License (2-clause, 3-clause)
- ✅ CC BY 4.0 (with attribution)

**Unacceptable Licenses:**
- ❌ GPL/LGPL (copyleft conflicts with Apache)
- ❌ Non-commercial only licenses
- ❌ Research-only licenses
- ❌ Custom restrictive licenses

**Before Adding:**
1. Verify model license file exists
2. Check training data licenses
3. Add attribution to this file
4. Update README.md model table

---

## 📞 Questions About Licensing?

**For AILive licensing:** See [LICENSE](LICENSE)  
**For commercial use inquiries:** ismail.t.abdullah@gmail.com  
**For model-specific licensing:** Refer to original model repositories

---

## 🔒 Legal Disclaimer

This CREDITS.md file is provided for informational purposes. While we have made every effort to ensure accuracy, users should:

1. Review original model licenses themselves
2. Consult legal counsel for commercial deployments
3. Verify licenses haven't changed since this was written
4. Understand that license interpretation may vary by jurisdiction

AILive authors are not liable for license misinterpretation or violations.

---

**Last Updated:** October 27, 2025  
**AILive Version:** 0.1 (Foundation)  
**Total Models:** 5  
**Commercial Safety:** 100% ✅
